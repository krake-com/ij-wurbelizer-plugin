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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.wurbelizer.idea.psi.WurbletBlock;
import org.wurbelizer.idea.psi.WurbletFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares the reconstructed wurblet level against the Java the wurbiler really generates.
 * <p>
 * The fixtures are the wurblet sources of the Tentackle framework together with the
 * {@code .java} files its build produced from them, so this test pins the reconstruction to
 * the actual behavior of {@code org.wurbelizer.wurbile.SourceWurbiler} rather than to an
 * expectation of ours.
 */
public class WurbletGoldenTest extends LightJavaCodeInsightFixtureTestCase {

  private static final String BEGIN = "// ----------------- begin wurblet code -----------------";
  private static final String END = "// ----------------- end wurblet code -----------------";

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  /** The minimal model driven wurblet. */
  public void testColumnLengths() throws IOException {
    assertReconstructs("ColumnLengths");
  }

  /** Two loops, two output blocks, ten fixed source strings. */
  public void testAttributeNames() throws IOException {
    assertReconstructs("AttributeNames");
  }

  /**
   * Asserts that the injected wurblet level document holds exactly the wurblet code of the
   * generated Java file, i.e. everything the wurbiler wrote between its markers except the
   * {@code out.print(source[N]);} calls, which carry the fixed text of the output level.
   *
   * @param name the wurblet name
   * @throws IOException if the expected file cannot be read
   */
  private void assertReconstructs(String name) throws IOException {
    myFixture.copyDirectoryToProject("wurblets", "wurblets");
    PsiFile file = PsiManager.getInstance(getProject()).findFile(
        myFixture.findFileInTempDir("wurblets/" + name + ".wrbl"));
    assertNotNull(file);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    List<String> expected = expectedWurbletCode(Path.of(getTestDataPath(), "wurblets", name + ".java.expected"));
    List<String> actual = actualWurbletCode((WurbletFile) file, expected.get(0));

    assertEquals("reconstruction of " + name + " differs",
                 String.join("\n", expected), String.join("\n", actual));
  }

  private List<String> expectedWurbletCode(Path path) throws IOException {
    List<String> lines = new ArrayList<>();
    boolean inside = false;
    for (String line : Files.readAllLines(path)) {
      String trimmed = line.trim();
      if (trimmed.equals(BEGIN)) {
        inside = true;
        continue;
      }
      if (trimmed.equals(END)) {
        break;
      }
      if (inside && !trimmed.isEmpty() && !trimmed.startsWith("out.print(source[")) {
        lines.add(trimmed);
      }
    }
    assertFalse("no wurblet code found in " + path, lines.isEmpty());
    return lines;
  }

  private List<String> actualWurbletCode(WurbletFile file, String firstLine) {
    WurbletBlock block = PsiTreeUtil.findChildOfType(file, WurbletBlock.class);
    assertNotNull("no wurblet level block", block);
    List<Pair<PsiElement, TextRange>> injected =
        InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(block);
    assertNotNull("no wurblet level injection", injected);
    assertFalse("no wurblet level injection", injected.isEmpty());

    List<String> lines = new ArrayList<>();
    boolean inside = false;
    for (String line : injected.get(0).getFirst().getContainingFile().getText().split("\n")) {
      String trimmed = line.trim();
      if (!inside && trimmed.equals(firstLine)) {
        inside = true;
      }
      if (inside && !trimmed.isEmpty()) {
        lines.add(trimmed);
      }
    }
    // drop the brace closing the class, which the wurbiler writes after the wurblet code
    if (!lines.isEmpty() && lines.get(lines.size() - 1).equals("}")) {
      lines.remove(lines.size() - 1);
    }
    return lines;
  }
}
