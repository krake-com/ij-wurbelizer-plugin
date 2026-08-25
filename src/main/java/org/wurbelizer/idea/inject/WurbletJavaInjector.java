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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.wurbelizer.idea.inject;

import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.lang.WurbletTokenTypes;
import org.wurbelizer.idea.model.WurbletDirectiveType;
import org.wurbelizer.idea.model.WurbletHeader;
import org.wurbelizer.idea.psi.WurbletDirective;
import org.wurbelizer.idea.psi.WurbletContent;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.psi.WurbletHostElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects Java into both code levels of a wurblet source.
 * <p>
 * Two disjoint documents are built for every file.
 * <p>
 * The <strong>wurblet level</strong> document reconstructs what the wurbiler generates: the
 * class header from the directives, then the {@code @[ ]@} blocks verbatim and every
 * {@code @( expr )@} as {@code out.print(expr);}. Because that is exactly the concatenation
 * {@code org.wurbelizer.wurbile.SourceWurbiler} writes, a loop opened in one block and closed
 * in another forms one continuous Java fragment, locals are visible across blocks, and the
 * members of the {@code @{extends}@} parent class resolve.
 * <p>
 * The <strong>output level</strong> document holds the generated text, with a synthetic
 * identifier standing in for each value extraction. It is a best effort parse; its errors are
 * suppressed by {@code WurbletOutputHighlightFilter}.
 */
public class WurbletJavaInjector implements MultiHostInjector {

  /** The default output stream of a wurblet, a field of {@code AbstractWurblet}. */
  public static final String DEFAULT_STREAM = "out";

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return List.of(WurbletContent.class);
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    if (!(context instanceof WurbletContent content)
        || !(content.getContainingFile() instanceof WurbletFile file)) {
      return;
    }
    WurbletHeader header = WurbletHeader.of(file);
    injectWurbletLevel(registrar, content, file, header);
    injectOutputLevel(registrar, content, file, header);
  }

  /**
   * Injects the wurblet level, i.e. the generator's own Java code.
   *
   * @param registrar the registrar
   * @param file the wurblet source
   * @param header the header of the wurblet source
   */
  private void injectWurbletLevel(@NotNull MultiHostRegistrar registrar, @NotNull WurbletContent content,
                                  @NotNull WurbletFile file, @NotNull WurbletHeader header) {
    record Place(String prefix, WurbletHostElement host, TextRange range) { }

    List<Place> places = new ArrayList<>();
    StringBuilder code = new StringBuilder();
    String stream = DEFAULT_STREAM;
    for (ASTNode node = content.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
      PsiElement psi = node.getPsi();
      if (node.getElementType() == WurbletTokenTypes.WURBLET_DIRECTIVE && psi instanceof WurbletDirective directive) {
        if (WurbletDirectiveType.forName(directive.getDirectiveName()) == WurbletDirectiveType.TO) {
          String target = directive.getFirstArgument();
          stream = target == null || target.isEmpty() ? DEFAULT_STREAM : target;
        }
        continue;
      }
      if (!(psi instanceof WurbletHostElement host)) {
        continue;
      }
      TextRange range = host.getRangeInsideHost();
      if (range.isEmpty()) {
        continue;
      }
      if (node.getElementType() == WurbletTokenTypes.WURBLET_BLOCK) {
        places.add(new Place(null, host, range));
        code.append(range.substring(host.getText()));
      }
      else if (node.getElementType() == WurbletTokenTypes.WURBLET_EXPR) {
        places.add(new Place(stream + ".print(", host, range));
      }
    }
    if (places.isEmpty()) {
      return;
    }

    String prefix = buildClassHeader(file, header, code.toString());
    registrar.startInjecting(JavaLanguage.INSTANCE);
    for (int i = 0; i < places.size(); i++) {
      Place place = places.get(i);
      boolean first = i == 0;
      boolean last = i == places.size() - 1;
      String placePrefix = (first ? prefix : "") + (place.prefix() == null ? "" : place.prefix());
      String placeSuffix = (place.prefix() == null ? "" : ");\n") + (last ? "\n}\n" : "");
      registrar.addPlace(placePrefix.isEmpty() ? null : placePrefix,
                         placeSuffix.isEmpty() ? null : placeSuffix,
                         place.host(), place.range());
    }
    registrar.doneInjecting();
  }

  /**
   * Builds the class header the wurbiler writes in front of the wurblet code.
   *
   * @param file the wurblet source
   * @param header the header of the wurblet source
   * @param code the concatenated wurblet level code, used to decide whether the generated
   *        {@code run()} makes sense
   * @return the header, never null
   */
  private String buildClassHeader(@NotNull WurbletFile file, @NotNull WurbletHeader header, @NotNull String code) {
    StringBuilder buf = new StringBuilder(256);
    if (header.getPackageName() != null) {
      buf.append("package ").append(header.getPackageName()).append(";\n");
    }
    for (String imp : header.getImports()) {
      buf.append("import ").append(imp).append(";\n");
    }
    buf.append("public class ").append(file.getWurbletName())
       .append(" extends ").append(header.getParentClass());
    if (!header.getInterfaces().isEmpty()) {
      buf.append(" implements ").append(String.join(", ", header.getInterfaces()));
    }
    buf.append(" {\n");
    if (code.contains("wurbel(")) {
      // the run() the wurbiler generates, so that wurbel() is not reported as unused
      buf.append("  @Override\n")
         .append("  public void run() throws WurbelException {\n")
         .append("    super.run();\n")
         .append("    try {\n")
         .append("      wurbel();\n")
         .append("    }\n")
         .append("    catch (Throwable t) {\n")
         .append("      if (t instanceof WurbelException) {\n")
         .append("        throw (WurbelException) t;\n")
         .append("      }\n")
         .append("      throw new WurbelException(\"wurblet \" + this + \" failed\", t);\n")
         .append("    }\n")
         .append("  }\n");
    }
    return buf.toString();
  }

  /**
   * Injects the output level, i.e. the code being generated.
   *
   * @param registrar the registrar
   * @param file the wurblet source
   * @param header the header of the wurblet source
   */
  private void injectOutputLevel(@NotNull MultiHostRegistrar registrar, @NotNull WurbletContent content,
                                 @NotNull WurbletFile file, @NotNull WurbletHeader header) {
    if (!WurbletJavaSupport.isJavaOutput(file, header)) {
      return;
    }
    WurbletOutputModel model = WurbletOutputModel.of(file);
    if (model.isEmpty()) {
      return;
    }
    WurbletJavaSupport.Wrapper wrapper = WurbletJavaSupport.wrapperOf(file);
    List<WurbletOutputModel.Place> places = model.getPlaces();
    registrar.startInjecting(JavaLanguage.INSTANCE);
    for (int i = 0; i < places.size(); i++) {
      WurbletOutputModel.Place place = places.get(i);
      String prefix = (i == 0 ? wrapper.getPrefix() : "") + place.prefix();
      String suffix = i == places.size() - 1 ? wrapper.getSuffix() : "";
      registrar.addPlace(prefix.isEmpty() ? null : prefix,
                         suffix.isEmpty() ? null : suffix,
                         place.host(), place.range());
    }
    registrar.doneInjecting();
  }
}
