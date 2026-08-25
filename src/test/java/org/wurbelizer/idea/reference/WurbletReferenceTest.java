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

package org.wurbelizer.idea.reference;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * Tests navigation from the directives.
 */
public class WurbletReferenceTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.wurbelizer.wurblet; public abstract class AbstractWurblet { }");
    myFixture.addClass("package org.wurbelizer.wurblet; public abstract class AbstractJavaWurblet extends AbstractWurblet { }");
    myFixture.addClass("package org.tentackle.wurblet; public class ModelWurblet extends org.wurbelizer.wurblet.AbstractJavaWurblet { }");
  }

  /** {@code @{include $currentDir/header.incl}@} navigates to the included file. */
  public void testIncludeResolves() {
    myFixture.addFileToProject("wurblets/header.incl", "@{extends ModelWurblet}@\n");
    configure("wurblets/Test.wrbl", "@{include $currentDir/head<caret>er.incl}@\n");
    PsiElement target = resolveAtCaret();
    assertTrue("expected header.incl, got " + target, target instanceof PsiFile psi && psi.getName().equals("header.incl"));
  }

  /** A relative include is resolved against the directory of the wurblet source. */
  public void testRelativeIncludeResolves() {
    myFixture.addFileToProject("wurblets/shared.incl", "@{args}@\n");
    configure("wurblets/Test.wrbl", "@{include sha<caret>red.incl}@\n");
    assertNotNull("relative include did not resolve", resolveAtCaret());
  }

  /** {@code @{extends}@} navigates to the parent class, resolved through the imports. */
  public void testExtendsResolvesThroughImports() {
    configure("wurblets/Test.wrbl", """
        @{import org.tentackle.wurblet.*}@
        @{extends Model<caret>Wurblet}@
        """);
    PsiElement target = resolveAtCaret();
    assertTrue("expected ModelWurblet, got " + target,
               target instanceof PsiClass psiClass
               && "org.tentackle.wurblet.ModelWurblet".equals(psiClass.getQualifiedName()));
  }

  /** {@code @{extends}@} accepts a fully qualified name. */
  public void testExtendsResolvesQualifiedName() {
    configure("wurblets/Test.wrbl", "@{extends org.tentackle.wurblet.Model<caret>Wurblet}@\n");
    assertTrue(resolveAtCaret() instanceof PsiClass);
  }

  /** {@code @{import}@} navigates to the package. */
  public void testImportResolvesPackage() {
    configure("wurblets/Test.wrbl", "@{import org.tentackle.wurb<caret>let.*}@\n");
    PsiElement target = resolveAtCaret();
    assertTrue("expected a package, got " + target,
               target instanceof PsiPackage psiPackage
               && "org.tentackle.wurblet".equals(psiPackage.getQualifiedName()));
  }

  /** {@code @{implements}@} navigates to each interface. */
  public void testImplementsResolves() {
    myFixture.addClass("package org.example; public interface Marker { }");
    configure("wurblets/Test.wrbl", "@{implements org.example.Mar<caret>ker}@\n");
    assertTrue(resolveAtCaret() instanceof PsiClass);
  }

  /**
   * Creates a file at the given path and puts the caret where the text says {@code <caret>}.
   * <p>
   * The fixture only processes the caret marker for files it creates itself, but these tests
   * need a specific directory so that {@code $currentDir} and relative includes resolve.
   *
   * @param path the path within the project
   * @param textWithCaret the file content, holding one caret marker
   */
  private void configure(String path, String textWithCaret) {
    int offset = textWithCaret.indexOf("<caret>");
    assertTrue("no caret marker", offset >= 0);
    PsiFile file = myFixture.addFileToProject(path, textWithCaret.replace("<caret>", ""));
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.getEditor().getCaretModel().moveToOffset(offset);
  }

  private PsiElement resolveAtCaret() {
    var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    assertNotNull("no reference at the caret", reference);
    return reference.resolve();
  }
}
