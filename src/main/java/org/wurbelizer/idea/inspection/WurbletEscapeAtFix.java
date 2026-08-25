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

package org.wurbelizer.idea.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Escapes an {@code @} that starts no wurblet construct.
 */
public class WurbletEscapeAtFix implements IntentionAction {

  private final int offset;

  /**
   * Creates the fix.
   *
   * @param offset the offset of the {@code @} within the file
   */
  public WurbletEscapeAtFix(int offset) {
    this.offset = offset;
  }

  @Override
  public @NotNull String getText() {
    return "Escape as '\\@'";
  }

  @Override
  public @NotNull String getFamilyName() {
    return "Wurblet";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file != null && offset < file.getTextLength() && file.getText().charAt(offset) == '@';
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      document.insertString(offset, "\\");
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
