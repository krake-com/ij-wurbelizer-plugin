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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.wurbelizer.idea.psi.WurbletBlock;
import org.wurbelizer.idea.psi.WurbletFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the whole pipeline over the real wurblet sources of the Tentackle framework.
 * <p>
 * The interesting property is that the reconstructed wurblet level must be <em>syntactically
 * valid Java</em>: these files use everything the wurbiler offers, from a template block
 * inside a {@code catch} ({@code Include}) over a generated nested {@code record} with
 * interpolated indentation ({@code UniqueDomainKey}) to a wurblet without any output at all
 * ({@code Inject}).
 */
public class WurbletSmokeTest extends LightJavaCodeInsightFixtureTestCase {

  private static final String[] WURBLETS = {
      "ColumnLengths", "AttributeNames", "UniqueDomainKey", "Include", "Inject", "Methods",
  };

  @Override
  protected String getTestDataPath() {
    return "src/test/testData";
  }

  /** The reconstructed wurblet level parses without a single syntax error. */
  public void testWurbletLevelIsValidJava() {
    myFixture.copyDirectoryToProject("wurblets", "wurblets");
    List<String> failures = new ArrayList<>();
    for (String name : WURBLETS) {
      VirtualFile virtualFile = myFixture.findFileInTempDir("wurblets/" + name + ".wrbl");
      assertNotNull(name + " not found", virtualFile);
      PsiFile file = PsiManager.getInstance(getProject()).findFile(virtualFile);
      assertTrue(name + " is not a wurblet source", file instanceof WurbletFile);
      myFixture.configureFromExistingVirtualFile(virtualFile);

      WurbletBlock block = PsiTreeUtil.findChildOfType(file, WurbletBlock.class);
      assertNotNull(name + " has no wurblet level block", block);
      List<Pair<PsiElement, TextRange>> injected =
          InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(block);
      assertNotNull(name + " has no wurblet level injection", injected);
      assertFalse(name + " has no wurblet level injection", injected.isEmpty());

      PsiFile java = injected.get(0).getFirst().getContainingFile();
      String errors = PsiTreeUtil.findChildrenOfType(java, PsiErrorElement.class).stream()
          .map(error -> error.getErrorDescription() + " at '" + context(java.getText(), error) + "'")
          .collect(Collectors.joining("\n    "));
      if (!errors.isEmpty()) {
        failures.add(name + ":\n    " + errors);
      }
    }
    assertTrue("the reconstructed wurblet level does not parse:\n" + String.join("\n", failures),
               failures.isEmpty());
  }

  private static String context(String text, PsiErrorElement error) {
    int start = Math.max(0, error.getTextRange().getStartOffset() - 40);
    int end = Math.min(text.length(), error.getTextRange().getEndOffset() + 40);
    return text.substring(start, end).replace('\n', '¶');
  }
}
