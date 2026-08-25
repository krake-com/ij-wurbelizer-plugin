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

package org.wurbelizer.idea.inspection;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.lang.WurbletTokenTypes;
import org.wurbelizer.idea.model.WurbletDirectiveType;
import org.wurbelizer.idea.model.WurbletVariables;
import org.wurbelizer.idea.psi.WurbletDirective;
import com.intellij.psi.util.PsiTreeUtil;
import org.wurbelizer.idea.psi.WurbletBlock;
import org.wurbelizer.idea.psi.WurbletContent;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.settings.WurbletSettings;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports what the wurbiler would report.
 * <p>
 * The checks are deliberately modelled on the diagnostics of
 * {@code org.wurbelizer.wurbile.AbstractWurbiler}, including its quirks: an unknown directive
 * aborts the compilation, and {@code @{args}@} silently keeps only its first argument because
 * the handler tests {@code if (stok.hasMoreTokens())} instead of looping.
 */
public class WurbletAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof WurbletDirective directive) {
      annotateDirective(directive, holder);
    }
    else if (element instanceof WurbletFile file) {
      annotateFile(file, holder);
    }
    else {
      ASTNode node = element.getNode();
      if (node != null && node.getFirstChildNode() == null) {
        annotateToken(element, node, holder);
      }
    }
  }

  private void annotateToken(@NotNull PsiElement element, @NotNull ASTNode node, @NotNull AnnotationHolder holder) {
    if (node.getElementType() == WurbletTokenTypes.BAD_AT) {
      holder.newAnnotation(HighlightSeverity.WARNING,
              "Unknown @-sequence: the wurbiler warns and keeps both characters")
          .range(element)
          .withFix(new WurbletEscapeAtFix(element.getTextRange().getStartOffset()))
          .create();
    }
    else if (node.getElementType() == WurbletTokenTypes.LEVEL_END
             && element.getParent() instanceof WurbletContent) {
      holder.newAnnotation(HighlightSeverity.ERROR, "No wurblet level is open here")
          .range(element)
          .create();
    }
  }

  private void annotateDirective(@NotNull WurbletDirective directive, @NotNull AnnotationHolder holder) {
    if (!directive.isClosed()) {
      holder.newAnnotation(HighlightSeverity.ERROR, "Unclosed directive, expected '}@'")
          .range(directive)
          .create();
    }
    String name = directive.getDirectiveName();
    ASTNode nameNode = directive.getNameNode();
    if (name == null || nameNode == null) {
      holder.newAnnotation(HighlightSeverity.ERROR, "Missing directive name")
          .range(directive)
          .create();
      return;
    }
    WurbletDirectiveType type = WurbletDirectiveType.forName(name);
    if (type == null) {
      holder.newAnnotation(HighlightSeverity.ERROR,
              "Unknown wurblet directive '" + nameNode.getText() + "', the wurbiler aborts compilation")
          .range(nameNode.getTextRange())
          .create();
      return;
    }
    List<String> arguments = directive.getArguments();
    switch (type) {
      case PHASE -> requireInteger(directive, holder, arguments, false);
      case INDENT -> requireInteger(directive, holder, arguments, true);
      case ARGS -> {
        if (arguments.size() > 1) {
          holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                  "Only the first argument is used: the wurbiler's handleArgs() does not loop")
              .range(argumentRange(directive, 1))
              .create();
        }
      }
      case INCLUDE -> annotateInclude(directive, holder, arguments);
      case PACKAGE, EXTENDS -> {
        if (arguments.isEmpty()) {
          holder.newAnnotation(HighlightSeverity.ERROR, "Missing argument for '" + name + "'")
              .range(directive)
              .create();
        }
      }
      default -> { }
    }
  }

  private void annotateInclude(@NotNull WurbletDirective directive, @NotNull AnnotationHolder holder,
                               @NotNull List<String> arguments) {
    if (arguments.isEmpty()) {
      holder.newAnnotation(HighlightSeverity.ERROR, "Missing filename for 'include'")
          .range(directive)
          .create();
      return;
    }
    WurbletFile file = directive.getContainingFile() instanceof WurbletFile wurbletFile ? wurbletFile : null;
    if (file == null) {
      return;
    }
    VirtualFile self = file.getOriginalFile().getVirtualFile();
    VirtualFile baseDir = self == null ? null : self.getParent();
    Map<String, String> variables = new LinkedHashMap<>();
    if (baseDir != null) {
      variables.put(WurbletVariables.CURRENT_DIR, baseDir.getPath());
    }
    if (self != null) {
      variables.put(WurbletVariables.CURRENT_FILE, self.getName());
    }
    variables.putAll(WurbletSettings.getInstance(file.getProject()).variables);
    String path = WurbletVariables.substitute(arguments.get(0), variables);
    if (WurbletVariables.resolve(path, baseDir) == null) {
      holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve include '" + path + "'")
          .range(argumentRange(directive, 0))
          .create();
    }
  }

  private void requireInteger(@NotNull WurbletDirective directive, @NotNull AnnotationHolder holder,
                              @NotNull List<String> arguments, boolean autoAllowed) {
    if (arguments.isEmpty()) {
      holder.newAnnotation(HighlightSeverity.ERROR, "Missing argument for '" + directive.getDirectiveName() + "'")
          .range(directive)
          .create();
      return;
    }
    String argument = arguments.get(0);
    if (autoAllowed && "auto".equalsIgnoreCase(argument)) {
      return;
    }
    try {
      Integer.parseInt(argument);
    }
    catch (NumberFormatException ex) {
      holder.newAnnotation(HighlightSeverity.ERROR,
              autoAllowed ? "Expected a number of columns or 'auto'" : "Expected a number")
          .range(argumentRange(directive, 0))
          .create();
    }
  }

  private void annotateFile(@NotNull WurbletFile file, @NotNull AnnotationHolder holder) {
    Map<WurbletDirectiveType, String> singletons = new LinkedHashMap<>();
    Set<String> interfaces = new HashSet<>();
    for (WurbletDirective directive : file.getDirectives()) {
      WurbletDirectiveType type = WurbletDirectiveType.forName(directive.getDirectiveName());
      if (type == WurbletDirectiveType.PACKAGE || type == WurbletDirectiveType.EXTENDS) {
        String value = directive.getFirstArgument();
        String previous = singletons.putIfAbsent(type, value);
        if (previous != null && !previous.equals(value)) {
          holder.newAnnotation(HighlightSeverity.ERROR,
                  "More than one '" + type.getDirectiveName() + "' directive, the wurbiler ignores this one")
              .range(directive)
              .create();
        }
      }
      else if (type == WurbletDirectiveType.IMPLEMENTS) {
        for (String iface : directive.getArguments()) {
          if (!interfaces.add(iface)) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Interface '" + iface + "' already added")
                .range(directive)
                .create();
          }
        }
      }
    }
    annotateUnclosedLevel(file, holder);
    annotateMissingWurbelMethod(file, holder);
  }

  /**
   * Reports a wurblet source whose code defines no {@code wurbel()} method.
   * <p>
   * The {@code run()} the wurbiler generates calls {@code wurbel()} unconditionally, so
   * without it the generated wurblet does not compile.
   *
   * @param file the wurblet source
   * @param holder the annotation holder
   */
  private void annotateMissingWurbelMethod(@NotNull WurbletFile file, @NotNull AnnotationHolder holder) {
    WurbletBlock first = null;
    StringBuilder code = new StringBuilder();
    for (WurbletBlock block : PsiTreeUtil.findChildrenOfType(file, WurbletBlock.class)) {
      if (first == null) {
        first = block;
      }
      code.append(block.getText());
    }
    if (first == null || code.indexOf("wurbel(") >= 0) {
      return;
    }
    ASTNode marker = first.getNode().getFirstChildNode();
    holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
            "No wurbel() method: the generated run() calls it unconditionally")
        .range(marker == null ? first.getTextRange() : marker.getTextRange())
        .create();
  }

  private void annotateUnclosedLevel(@NotNull WurbletFile file, @NotNull AnnotationHolder holder) {
    WurbletContent content = file.getContent();
    if (content == null) {
      return;
    }
    ASTNode last = content.getNode().getLastChildNode();
    if (last == null) {
      return;
    }
    if (last.getElementType() != WurbletTokenTypes.WURBLET_BLOCK
        && last.getElementType() != WurbletTokenTypes.WURBLET_EXPR
        && last.getElementType() != WurbletTokenTypes.WURBLET_DIRECTIVE) {
      return;
    }
    ASTNode terminator = last.getLastChildNode();
    if (terminator != null && terminator.getElementType() == WurbletTokenTypes.LEVEL_END) {
      return;
    }
    ASTNode opener = last.getFirstChildNode();
    holder.newAnnotation(HighlightSeverity.ERROR, "Unclosed wurblet level at end of file")
        .range(opener == null ? last.getTextRange() : opener.getTextRange())
        .create();
  }

  private static TextRange argumentRange(@NotNull WurbletDirective directive, int index) {
    List<TextRange> ranges = directive.getArgumentRanges();
    int base = directive.getTextRange().getStartOffset();
    if (index < ranges.size()) {
      return ranges.get(index).shiftRight(base);
    }
    return directive.getTextRange();
  }
}
