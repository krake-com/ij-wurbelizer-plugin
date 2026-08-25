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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.wurbelizer.idea.lang.WurbletTokenTypes;
import org.wurbelizer.idea.psi.WurbletContent;
import org.wurbelizer.idea.psi.WurbletEscaping;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.psi.WurbletOutputSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * The output level of a wurblet source, assembled into one Java fragment.
 * <p>
 * The text runs of the output level are the fragment's content, and every {@code @( )@}
 * value extraction between them is replaced by a synthetic identifier. Without such a
 * placeholder the concatenation would not even tokenize: {@code int CL_@(name)@ = @(size)@;}
 * would collapse into {@code int CL_ = ;}.
 * <p>
 * The placeholder is glued to the text on its left, which is the name building case
 * ({@code CL_@(x)@} becomes {@code CL__wv1_}, a single identifier), and separated by a blank
 * from an identifier on its right, which is the modifier case ({@code @(lead)@int} becomes
 * {@code _wv0_ int}).
 */
public final class WurbletOutputModel {

  /** One injected place: a synthetic prefix followed by a run of output level text. */
  public record Place(@NotNull String prefix, @NotNull WurbletOutputSegment host, @NotNull TextRange range) {
  }

  private static final String PLACEHOLDER_PREFIX = "_wv";
  private static final String PLACEHOLDER_SUFFIX = "_";

  private final List<Place> places;
  private final String text;

  private WurbletOutputModel(List<Place> places, String text) {
    this.places = List.copyOf(places);
    this.text = text;
  }

  /**
   * Builds the model of a wurblet source.
   *
   * @param file the wurblet source
   * @return the model, never null
   */
  public static @NotNull WurbletOutputModel of(@NotNull WurbletFile file) {
    List<WurbletOutputSegment> segments = new ArrayList<>();
    List<Integer> holesBefore = new ArrayList<>();    // number of value extractions before each segment
    int holes = 0;
    WurbletContent root = file.getContent();
    if (root == null) {
      return new WurbletOutputModel(List.of(), "");
    }
    for (ASTNode node = root.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
      if (node.getElementType() == WurbletTokenTypes.WURBLET_EXPR) {
        holes++;
      }
      else if (node.getElementType() == WurbletTokenTypes.OUTPUT_SEGMENT
               && node.getPsi() instanceof WurbletOutputSegment segment) {
        segments.add(segment);
        holesBefore.add(holes);
      }
    }

    List<Place> places = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    int emitted = 0;
    for (int i = 0; i < segments.size(); i++) {
      WurbletOutputSegment segment = segments.get(i);
      TextRange range = segment.getRangeInsideHost();
      String content = WurbletEscaping.decode(range.substring(segment.getText()));
      StringBuilder prefix = new StringBuilder();
      while (emitted < holesBefore.get(i)) {
        prefix.append(PLACEHOLDER_PREFIX).append(emitted).append(PLACEHOLDER_SUFFIX);
        emitted++;
      }
      if (!prefix.isEmpty() && !content.isEmpty() && isIdentifierChar(content.charAt(0))) {
        prefix.append(' ');
      }
      places.add(new Place(prefix.toString(), segment, range));
      text.append(prefix).append(content);
    }
    return new WurbletOutputModel(places, text.toString());
  }

  /**
   * Returns the injected places, in source order.
   *
   * @return the places, never null
   */
  public @NotNull List<Place> getPlaces() {
    return places;
  }

  /**
   * Returns the assembled fragment text, i.e. what the injected document holds between
   * the wrapper's prefix and suffix.
   *
   * @return the text, never null
   */
  public @NotNull String getText() {
    return text;
  }

  /**
   * Returns whether the output level holds anything worth injecting.
   *
   * @return true if there is content
   */
  public boolean isEmpty() {
    return places.isEmpty() || text.isBlank();
  }

  private static boolean isIdentifierChar(char c) {
    return Character.isJavaIdentifierPart(c);
  }
}
