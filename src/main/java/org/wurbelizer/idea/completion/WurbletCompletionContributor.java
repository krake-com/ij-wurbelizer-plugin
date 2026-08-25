/*
 * Wurbelizer - https://wurbelizer.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.wurbelizer.idea.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.inject.WurbletJavaSupport;
import org.wurbelizer.idea.lang.WurbletLanguage;
import org.wurbelizer.idea.model.WurbletDirectiveType;
import org.wurbelizer.idea.model.WurbletHeader;
import org.wurbelizer.idea.model.WurbletVariables;
import org.wurbelizer.idea.psi.WurbletDirective;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.psi.WurbletOutputSegment;
import org.wurbelizer.idea.reference.WurbletIncludeReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completion for everything that is not covered by the Java injection: the wurbiler
 * directives and their arguments, and a plain fallback for the output level of wurblets that
 * do not generate Java.
 */
public class WurbletCompletionContributor extends CompletionContributor {

  private static final Pattern STREAM_DECLARATION =
      Pattern.compile("\\bPrintStream\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

  private static final String[] JAVA_KEYWORDS = {
      "abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
      "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
      "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new",
      "package", "private", "protected", "public", "record", "return", "sealed", "short",
      "static", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
      "try", "void", "volatile", "while", "var", "yield",
  };

  /** Creates the contributor. */
  public WurbletCompletionContributor() {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement().withLanguage(WurbletLanguage.INSTANCE),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               complete(parameters, result);
             }
           });
  }

  private void complete(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (!(position.getContainingFile() instanceof WurbletFile file)) {
      return;
    }
    WurbletDirective directive = PsiTreeUtil.getParentOfType(position, WurbletDirective.class, false);
    if (directive != null) {
      completeDirective(parameters, result, file, directive);
      return;
    }
    if (PsiTreeUtil.getParentOfType(position, WurbletOutputSegment.class, false) != null) {
      completeOutputFallback(parameters, result, file);
    }
  }

  private void completeDirective(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result,
                                 @NotNull WurbletFile file, @NotNull WurbletDirective directive) {
    ASTNode nameNode = directive.getNameNode();
    int offset = parameters.getOffset();
    if (nameNode == null || offset <= nameNode.getTextRange().getEndOffset()) {
      CompletionResultSet names = result.withPrefixMatcher(prefixOf(parameters, false));
      for (WurbletDirectiveType type : WurbletDirectiveType.values()) {
        names.addElement(LookupElementBuilder.create(type.getDirectiveName())
            .withTypeText(type.getParameters())
            .withTailText("  " + type.getDescription(), true));
      }
      return;
    }
    WurbletDirectiveType type = WurbletDirectiveType.forName(directive.getDirectiveName());
    if (type == null) {
      return;
    }
    CompletionResultSet arguments = result.withPrefixMatcher(prefixOf(parameters, true));
    switch (type) {
      case EXTENDS, IMPLEMENTS -> completeClasses(parameters, arguments, file);
      case IMPORT -> completeImports(arguments, file);
      case INCLUDE -> completeIncludes(arguments, file);
      case TO -> completeStreams(arguments, file);
      case INDENT -> arguments.addElement(LookupElementBuilder.create("auto"));
      default -> { }
    }
  }

  private void completeClasses(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result,
                               @NotNull WurbletFile file) {
    String prefix = result.getPrefixMatcher().getPrefix();
    if (prefix.isEmpty()) {
      return;   // far too many candidates
    }
    Project project = file.getProject();
    GlobalSearchScope scope = WurbletJavaSupport.scopeOf(file);
    WurbletHeader header = WurbletHeader.of(file);
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    // the names are collected first: querying the classes from within processAllClassNames
    // would nest two stub index operations, which the platform forbids
    List<String> names = new ArrayList<>();
    cache.processAllClassNames(name -> {
      if (result.getPrefixMatcher().prefixMatches(name)) {
        names.add(name);
      }
      return true;
    }, scope, null);
    for (String name : names) {
      for (PsiClass psiClass : cache.getClassesByName(name, scope)) {
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null) {
          result.addElement(classElement(psiClass, qualifiedName, header));
        }
      }
    }
  }

  /**
   * Creates the lookup element of a class.
   * <p>
   * The simple name is inserted when the {@code @{import}@} directives of the wurblet make it
   * resolvable, otherwise the fully qualified name, which the wurbiler writes verbatim into
   * the {@code extends} clause and which therefore always works.
   *
   * @param psiClass the class
   * @param qualifiedName the qualified name of the class
   * @param header the header of the wurblet source
   * @return the lookup element, never null
   */
  private LookupElement classElement(@NotNull PsiClass psiClass, @NotNull String qualifiedName,
                                     @NotNull WurbletHeader header) {
    String simpleName = psiClass.getName() == null ? qualifiedName : psiClass.getName();
    String packageName = qualifiedName.length() > simpleName.length()
                         ? qualifiedName.substring(0, qualifiedName.length() - simpleName.length() - 1) : "";
    boolean importable = isImportable(header, packageName);
    return LookupElementBuilder.create(psiClass, simpleName)
        .withTypeText(packageName, true)
        .withInsertHandler((InsertionContext context, LookupElement item) -> {
          if (!importable) {
            context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), qualifiedName);
          }
        });
  }

  private static boolean isImportable(@NotNull WurbletHeader header, @NotNull String packageName) {
    if (packageName.isEmpty() || packageName.equals("java.lang") || packageName.equals(header.getPackageName())) {
      return true;
    }
    for (String imp : header.getImports()) {
      if (imp.equals(packageName + ".*") || imp.startsWith(packageName + ".") && imp.indexOf('.', packageName.length() + 1) < 0) {
        return true;
      }
    }
    return false;
  }

  private void completeImports(@NotNull CompletionResultSet result, @NotNull WurbletFile file) {
    String prefix = result.getPrefixMatcher().getPrefix();
    int ndx = prefix.lastIndexOf('.');
    String parentName = ndx < 0 ? "" : prefix.substring(0, ndx);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiPackage parent = facade.findPackage(parentName);
    if (parent == null) {
      return;
    }
    GlobalSearchScope scope = WurbletJavaSupport.scopeOf(file);
    for (PsiPackage subPackage : parent.getSubPackages(scope)) {
      String qualifiedName = subPackage.getQualifiedName();
      result.addElement(LookupElementBuilder.create(qualifiedName)
          .withPresentableText(qualifiedName)
          .withTailText(".*", true));
      result.addElement(LookupElementBuilder.create(qualifiedName + ".*"));
    }
    for (PsiClass psiClass : parent.getClasses(scope)) {
      String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName != null) {
        result.addElement(LookupElementBuilder.create(psiClass, qualifiedName));
      }
    }
  }

  private void completeIncludes(@NotNull CompletionResultSet result, @NotNull WurbletFile file) {
    VirtualFile self = file.getOriginalFile().getVirtualFile();
    VirtualFile baseDir = self == null ? null : self.getParent();
    if (baseDir == null) {
      return;
    }
    String prefix = result.getPrefixMatcher().getPrefix();
    int slash = prefix.lastIndexOf('/');
    String directoryPart = slash < 0 ? "" : prefix.substring(0, slash);
    VirtualFile directory = baseDir;
    if (!directoryPart.isEmpty()) {
      String resolved = WurbletVariables.substitute(directoryPart,
          WurbletIncludeReference.variablesOf(file, self, baseDir));
      directory = WurbletVariables.resolve(resolved, baseDir);
    }
    else {
      result.addElement(LookupElementBuilder.create("$currentDir/").withTypeText("directory of this wurblet", true));
    }
    if (directory == null || !directory.isDirectory()) {
      return;
    }
    String lead = directoryPart.isEmpty() ? "" : directoryPart + '/';
    for (VirtualFile child : directory.getChildren()) {
      if (child.equals(self)) {
        continue;
      }
      result.addElement(LookupElementBuilder.create(lead + child.getName())
          .withIcon(child.isDirectory() ? com.intellij.icons.AllIcons.Nodes.Folder : null));
    }
  }

  private void completeStreams(@NotNull CompletionResultSet result, @NotNull WurbletFile file) {
    Set<String> streams = new LinkedHashSet<>();
    streams.add("out");
    Matcher matcher = STREAM_DECLARATION.matcher(file.getText());
    while (matcher.find()) {
      streams.add(matcher.group(1));
    }
    for (String stream : streams) {
      result.addElement(LookupElementBuilder.create(stream));
    }
  }

  private void completeOutputFallback(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result,
                                      @NotNull WurbletFile file) {
    if (WurbletJavaSupport.isJavaOutput(file, WurbletHeader.of(file))) {
      return;   // the injected Java document does the job
    }
    CompletionResultSet plain = result.withPrefixMatcher(prefixOf(parameters, true));
    for (String keyword : JAVA_KEYWORDS) {
      plain.addElement(LookupElementBuilder.create(keyword).bold());
    }
    completeClasses(parameters, plain, file);
  }

  /**
   * Returns the completion prefix.
   *
   * @param parameters the completion parameters
   * @param wordOnly true to cut at the last whitespace, for arguments that follow the command
   * @return the prefix, never null
   */
  private static @NotNull String prefixOf(@NotNull CompletionParameters parameters, boolean wordOnly) {
    PsiElement position = parameters.getPosition();
    int start = position.getTextRange().getStartOffset();
    String text = position.getText();
    int end = Math.max(0, Math.min(parameters.getOffset() - start, text.length()));
    String prefix = text.substring(0, end).replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "");
    if (wordOnly) {
      int ndx = prefix.length();
      while (ndx > 0 && !Character.isWhitespace(prefix.charAt(ndx - 1))) {
        ndx--;
      }
      prefix = prefix.substring(ndx);
    }
    return prefix;
  }
}
