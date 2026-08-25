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

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.wurbelizer.idea.lang.WurbletFileType;

import java.util.List;

/**
 * Tests code completion on both code levels.
 */
public class WurbletCompletionTest extends LightJavaCodeInsightFixtureTestCase {

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
          public void run() throws org.wurbelizer.wurbel.WurbelException { }
          public String getOption(String name) { return null; }
        }
        """);
    myFixture.addClass("""
        package org.wurbelizer.wurblet;
        public abstract class AbstractJavaWurblet extends AbstractWurblet {
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
          public java.util.List<Attribute> getRelations() { return null; }
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
   * The wurbiler directives are completed after the opening brace. A unique match is inserted
   * right away, so the result is checked on the document.
   */
  public void testDirectiveNames() {
    complete("@{ex<caret>}@\n");
    assertTrue(myFixture.getFile().getText().startsWith("@{extends}@"));
  }

  /** All twelve directives are offered on an empty command word. */
  public void testAllDirectives() {
    List<String> lookup = complete("@{<caret>}@\n");
    assertContainsElements(lookup, "include", "package", "import", "extends", "implements",
                           "args", "to", "phase", "indent", "comment", "code", "config");
  }

  /**
   * The parent class of {@code @{extends}@} is completed from the project classes. The match
   * is unique, so it is inserted right away instead of showing a lookup.
   */
  public void testExtendsCompletesClasses() {
    complete("""
        @{package org.tentackle.wurblet}@
        @{extends ModelWurb<caret>}@
        """);
    assertTrue(myFixture.getFile().getText().contains("@{extends ModelWurblet}@"));
  }

  /**
   * The payoff at wurblet level: members of the {@code @{extends}@} parent class resolve.
   */
  public void testWurbletLevelCompletesParentClassMembers() {
    List<String> lookup = complete("""
        @{package org.tentackle.wurblet}@
        @{import org.tentackle.model.*}@
        @{import org.wurbelizer.wurbel.*}@
        @{extends ModelWurblet}@
        @[
          private void wurbel() throws WurbelException {
            getEntity().<caret>
          }
        ]@
        """);
    assertContainsElements(lookup, "getAttributes", "getRelations");
  }

  /**
   * A local declared in one {@code @[ ]@} block is visible in a later one, because both are
   * places of the same injected document.
   */
  public void testLocalsAreVisibleAcrossBlocks() {
    List<String> lookup = complete("""
        @{package org.tentackle.wurblet}@
        @{import org.tentackle.model.*}@
        @{import org.wurbelizer.wurbel.*}@
        @{extends ModelWurblet}@
        @[
          private void wurbel() throws WurbelException {
            for (Attribute attr: getEntity().getAttributes()) {
        ]@
        text
        @[
              attr.<caret>
            }
          }
        ]@
        """);
    assertContainsElements(lookup, "getName", "getSize");
  }

  /** A value extraction is wurblet level code and resolves against the surrounding block. */
  public void testValueExtractionCompletes() {
    List<String> lookup = complete("""
        @{package org.tentackle.wurblet}@
        @{import org.tentackle.model.*}@
        @{import org.wurbelizer.wurbel.*}@
        @{extends ModelWurblet}@
        @[
          private void wurbel() throws WurbelException {
            for (Attribute attr: getEntity().getAttributes()) {
        ]@
          int CL_@(attr.<caret>)@;
        @[
            }
          }
        ]@
        """);
    assertContainsElements(lookup, "getName", "getSize");
  }

  /**
   * The output level of a Java wurblet is Java too, so a variable declared in the generated
   * code resolves and offers its members.
   */
  public void testOutputLevelCompletesMembers() {
    List<String> lookup = complete("""
        @{package org.tentackle.wurblet}@
        @{import org.wurbelizer.wurblet.*}@
        @{extends AbstractJavaWurblet}@
        @[
          private void wurbel() {
        ]@
          void method() {
            org.tentackle.model.Attribute attribute = null;
            attribute.<caret>
          }
        @[
          }
        ]@
        """);
    assertContainsElements(lookup, "getName", "getSize");
  }

  /**
   * Class names are offered in both levels. As in an ordinary Java file, a class that is not
   * covered by an {@code @{import}@} directive shows up on the second completion invocation,
   * and it is inserted fully qualified: an injected document cannot grow an import statement,
   * because its import section belongs to the synthetic prefix.
   */
  public void testClassNamesAreInsertedFullyQualified() {
    myFixture.configureByText(WurbletFileType.INSTANCE, """
        @{package org.tentackle.wurblet}@
        @{import org.wurbelizer.wurbel.*}@
        @{import org.wurbelizer.wurblet.*}@
        @{extends AbstractJavaWurblet}@
        @[
          private void wurbel() throws WurbelException {
            Attribu<caret>
          }
        ]@
        """);
    myFixture.complete(com.intellij.codeInsight.completion.CompletionType.BASIC, 2);
    assertTrue("class name completion failed: " + myFixture.getFile().getText(),
               myFixture.getFile().getText().contains("org.tentackle.model.Attribute"));
    assertFalse("an import must not be written into the synthetic prefix",
                myFixture.getFile().getText().contains("import org.tentackle.model.Attribute;"));
  }

  /** A class covered by an {@code @{import}@} directive keeps its simple name. */
  public void testImportedClassKeepsSimpleName() {
    myFixture.configureByText(WurbletFileType.INSTANCE, """
        @{package org.tentackle.wurblet}@
        @{import org.tentackle.model.*}@
        @{import org.wurbelizer.wurbel.*}@
        @{import org.wurbelizer.wurblet.*}@
        @{extends AbstractJavaWurblet}@
        @[
          private void wurbel() throws WurbelException {
            Attribu<caret>
          }
        ]@
        """);
    myFixture.complete(com.intellij.codeInsight.completion.CompletionType.BASIC, 2);
    assertTrue("simple name expected: " + myFixture.getFile().getText(),
               myFixture.getFile().getText().contains("    Attribute"));
  }

  /** {@code @{to}@} offers the default stream and the streams declared in the wurblet. */
  public void testStreamCompletion() {
    List<String> lookup = complete("""
        @{package org.tentackle.wurblet}@
        @[
          private void wurbel() {
            PrintStream implOut = null;
        ]@
        @{to <caret>}@
        @[
          }
        ]@
        """);
    assertContainsElements(lookup, "out", "implOut");
  }

  private List<String> complete(String text) {
    myFixture.configureByText(WurbletFileType.INSTANCE, text);
    myFixture.completeBasic();
    List<String> lookup = myFixture.getLookupElementStrings();
    return lookup == null ? List.of() : lookup;
  }
}
