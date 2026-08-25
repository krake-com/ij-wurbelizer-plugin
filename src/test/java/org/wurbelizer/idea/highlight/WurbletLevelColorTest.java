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

package org.wurbelizer.idea.highlight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.wurbelizer.idea.lang.WurbletFileType;
import org.wurbelizer.idea.psi.WurbletBlock;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests that the two code levels are told apart by their background.
 */
public class WurbletLevelColorTest extends LightJavaCodeInsightFixtureTestCase {

  private static final String SOURCE = """
      @{extends AbstractJavaWurblet}@
      @[
        private void wurbel() {
      ]@
        int x = @(size)@;
      @[
        }
      ]@
      """;

  /** A directive belongs to the wurblet level: it configures the generator. */
  public void testDirectiveIsWurbletLevel() {
    assertBackground(SOURCE, "@{extends AbstractJavaWurblet}@",
                     WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND);
  }

  /** A block carries the wurblet level background, its markers included. */
  public void testBlockIsWurbletLevel() {
    assertBackground(SOURCE, "@[\n  private void wurbel() {\n]@",
                     WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND);
  }

  /** A value extraction is generator code spliced into the output, so it is wurblet level. */
  public void testValueExtractionIsWurbletLevel() {
    assertBackground(SOURCE, "@(size)@", WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND);
  }

  /** The text between the levels carries the output level background. */
  public void testTextIsOutputLevel() {
    assertBackground(SOURCE, "\n  int x = ", WurbletSyntaxHighlighter.OUTPUT_LEVEL_BACKGROUND);
  }

  /**
   * The lexer emits the escapes of every level as the same token types, so a {@code \n} within
   * a wurblet level string literal must not break the band the way a token keyed background
   * would.
   */
  public void testStringEscapeDoesNotBreakTheBand() {
    String source = """
        @[
          private void wurbel() {
            out.print("a\\nb");
          }
        ]@
        """;
    assertBackground(source, source.substring(source.indexOf("@["), source.indexOf("]@") + 2),
                     WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND);
  }

  /**
   * Without the suppression the platform would paint its uniform injected fragment background
   * over both levels and neither tint would show.
   */
  public void testHostSuppressesTheInjectedBackground() {
    myFixture.configureByText(WurbletFileType.INSTANCE, SOURCE);
    WurbletBlock block = PsiTreeUtil.findChildOfType(myFixture.getFile(), WurbletBlock.class);
    assertNotNull(block);
    assertTrue(block instanceof InjectionBackgroundSuppressor);
  }

  /**
   * The keys carry no fallback, so without the bundled scheme files both levels would resolve
   * to nothing and stay indistinguishable.
   */
  public void testEveryShippedSchemeDefinesTwoDistinctBackgrounds() {
    for (EditorColorsScheme scheme : EditorColorsManager.getInstance().getAllSchemes()) {
      TextAttributes wurblet = scheme.getAttributes(WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND);
      TextAttributes output = scheme.getAttributes(WurbletSyntaxHighlighter.OUTPUT_LEVEL_BACKGROUND);
      assertNotNull(scheme.getName() + " defines no wurblet level background", wurblet);
      assertNotNull(scheme.getName() + " defines no output level background", output);
      assertNotNull(scheme.getName() + " leaves the wurblet level transparent", wurblet.getBackgroundColor());
      assertNotNull(scheme.getName() + " leaves the output level transparent", output.getBackgroundColor());
      assertFalse(scheme.getName() + " colors both levels alike",
                  wurblet.getBackgroundColor().equals(output.getBackgroundColor()));
    }
  }

  /**
   * Asserts that exactly the range of the given text fragment is painted with the given key.
   *
   * @param source the wurblet source
   * @param fragment the text fragment, must occur in the source
   * @param key the expected attributes key
   */
  private void assertBackground(String source, String fragment, TextAttributesKey key) {
    int start = source.indexOf(fragment);
    assertTrue("fragment not found in source", start >= 0);
    TextRange expected = TextRange.from(start, fragment.length());

    myFixture.configureByText(WurbletFileType.INSTANCE, source);
    List<HighlightInfo> infos = myFixture.doHighlighting().stream()
        .filter(info -> key.equals(info.forcedTextAttributesKey))
        .toList();
    boolean found = infos.stream()
        .anyMatch(info -> info.getStartOffset() == expected.getStartOffset()
                          && info.getEndOffset() == expected.getEndOffset());
    assertTrue("expected " + key.getExternalName() + " over " + expected + ", got: "
               + infos.stream().map(i -> new TextRange(i.getStartOffset(), i.getEndOffset()).toString())
                      .collect(Collectors.joining(", ")),
               found);
  }
}
