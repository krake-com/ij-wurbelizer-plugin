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

package org.wurbelizer.idea.inject;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.wurbelizer.idea.psi.WurbletBlock;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.psi.WurbletOutputSegment;

import java.util.List;

/**
 * Tests the reconstruction of both code levels as Java.
 */
public class WurbletInjectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
        package org.wurbelizer.wurbel;
        public class WurbelException extends Exception {
          public WurbelException(String message) { super(message); }
          public WurbelException(String message, Throwable cause) { super(message, cause); }
        }
        """);
    myFixture.addClass("""
        package org.wurbelizer.wurblet;
        public abstract class AbstractWurblet {
          protected java.io.PrintStream out;
          protected String[] source;
          public void run() throws org.wurbelizer.wurbel.WurbelException { }
          public String getOption(String name) { return null; }
        }
        """);
    myFixture.addClass("""
        package org.wurbelizer.wurblet;
        public abstract class AbstractJavaWurblet extends AbstractWurblet {
          public String getClassName() { return null; }
          public boolean isInterface() { return false; }
        }
        """);
    myFixture.addClass("""
        package org.tentackle.model;
        public class Attribute {
          public String getName() { return null; }
          public Integer getSize() { return null; }
        }
        """);
    myFixture.addClass("""
        package org.tentackle.model;
        public class Entity {
          public java.util.List<Attribute> getAttributes() { return null; }
        }
        """);
    myFixture.addClass("""
        package org.tentackle.wurblet;
        public class ModelWurblet extends org.wurbelizer.wurblet.AbstractJavaWurblet {
          public org.tentackle.model.Entity getEntity() { return null; }
        }
        """);
  }

  /**
   * The wurblet level document must be the class the wurbiler generates: header from the
   * directives, {@code @[ ]@} blocks verbatim and every {@code @( )@} as {@code out.print(...)}.
   */
  public void testWurbletLevelReproducesTheGeneratedClass() {
    myFixture.addFileToProject("wurblets/header.incl", """
        @{package org.tentackle.wurblet}@
        @{import java.util.*}@
        @{import org.tentackle.model.*}@
        @{import org.wurbelizer.wurbel.*}@
        @{import org.wurbelizer.wurblet.*}@
        @{extends ModelWurblet}@
        @{args}@
        """);
    PsiFile file = myFixture.addFileToProject("wurblets/ColumnLengths.wrbl", """
        @{include $currentDir/header.incl}@
        @[
          private void wurbel() {
            String lead = getOption("noif") == null ? "" : "public static final ";
            for (Attribute attr: getEntity().getAttributes())  {
        ]@

          /** maximum number of characters for '@(attr)@'. */
          @(lead)@int CL_@(attr.getName().toUpperCase(Locale.ROOT))@ = @(attr.getSize())@;
        @[
            }
          }
        ]@
        """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    String injected = wurbletLevelText((WurbletFile) file);
    assertNotNull("no wurblet level injection", injected);

    assertTrue("package missing:\n" + injected, injected.contains("package org.tentackle.wurblet;"));
    assertTrue("import missing:\n" + injected, injected.contains("import org.tentackle.model.*;"));
    assertTrue("class header wrong:\n" + injected,
               injected.contains("public class ColumnLengths extends ModelWurblet {"));
    assertTrue("run() missing:\n" + injected, injected.contains("public void run() throws WurbelException {"));
    assertTrue("wurblet code missing:\n" + injected, injected.contains("private void wurbel() {"));
    assertTrue("value extraction wrong:\n" + injected, injected.contains("out.print(attr);"));
    assertTrue("value extraction wrong:\n" + injected, injected.contains("out.print(lead);"));
    assertTrue("value extraction wrong:\n" + injected,
               injected.contains("out.print(attr.getName().toUpperCase(Locale.ROOT));"));
    assertTrue("value extraction wrong:\n" + injected, injected.contains("out.print(attr.getSize());"));
    assertTrue("class not closed:\n" + injected, injected.trim().endsWith("}"));
  }

  /** The output level must be a parsable Java fragment with placeholders for the holes. */
  public void testOutputLevelUsesPlaceholders() {
    PsiFile file = myFixture.addFileToProject("wurblets/Simple.wrbl", """
        @{package org.tentackle.wurblet}@
        @{import org.wurbelizer.wurblet.*}@
        @{extends AbstractJavaWurblet}@
        @[
          private void wurbel() {
        ]@
          @(lead)@int CL_@(name)@ = @(size)@;
        @[
          }
        ]@
        """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    String injected = outputLevelText((WurbletFile) file);
    assertNotNull("no output level injection", injected);
    assertTrue("wrapper missing:\n" + injected, injected.contains("class __WurbletOutput__"));
    assertTrue("placeholders wrong:\n" + injected, injected.contains("_wv0_ int CL__wv1_ = _wv2_;"));
  }

  /** A wurblet that does not extend AbstractJavaWurblet generates something else than Java. */
  public void testNonJavaWurbletHasNoOutputInjection() {
    PsiFile file = myFixture.addFileToProject("wurblets/Plain.wrbl", """
        @{package org.tentackle.wurblet}@
        @{import org.wurbelizer.wurblet.*}@
        @{extends AbstractWurblet}@
        @[
          private void wurbel() {
        ]@
        some plain text
        @[
          }
        ]@
        """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertNull("output level must not be injected", outputLevelText((WurbletFile) file));
    assertNotNull("wurblet level must be injected", wurbletLevelText((WurbletFile) file));
  }

  private String wurbletLevelText(WurbletFile file) {
    WurbletBlock block = PsiTreeUtil.findChildOfType(file, WurbletBlock.class);
    return block == null ? null : injectedText(file, block);
  }

  private String outputLevelText(WurbletFile file) {
    for (WurbletOutputSegment segment : PsiTreeUtil.findChildrenOfType(file, WurbletOutputSegment.class)) {
      String text = injectedText(file, segment);
      if (text != null) {
        return text;
      }
    }
    return null;
  }

  private String injectedText(WurbletFile file, com.intellij.psi.PsiElement host) {
    List<com.intellij.openapi.util.Pair<com.intellij.psi.PsiElement, com.intellij.openapi.util.TextRange>> files =
        InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(host);
    return files == null || files.isEmpty() ? null : files.get(0).getFirst().getContainingFile().getText();
  }
}
