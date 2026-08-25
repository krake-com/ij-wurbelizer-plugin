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

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.lang.WurbletIcons;

import javax.swing.Icon;
import java.util.Map;

/** Color settings page of the wurblet language. */
public class WurbletColorSettingsPage implements ColorSettingsPage {

  private static final AttributesDescriptor[] DESCRIPTORS = {
      new AttributesDescriptor("Level marker", WurbletSyntaxHighlighter.MARKER),
      new AttributesDescriptor("Directive//Command word", WurbletSyntaxHighlighter.DIRECTIVE_NAME),
      new AttributesDescriptor("Directive//Arguments", WurbletSyntaxHighlighter.DIRECTIVE_ARGUMENT),
      new AttributesDescriptor("Wurblet level code", WurbletSyntaxHighlighter.WURBLET_LEVEL),
      new AttributesDescriptor("Wurblet level background", WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND),
      new AttributesDescriptor("Output level text", WurbletSyntaxHighlighter.OUTPUT_LEVEL),
      new AttributesDescriptor("Output level background", WurbletSyntaxHighlighter.OUTPUT_LEVEL_BACKGROUND),
      new AttributesDescriptor("Escape sequence", WurbletSyntaxHighlighter.ESCAPE),
      new AttributesDescriptor("Unknown @-sequence", WurbletSyntaxHighlighter.BAD_AT),
  };

  @Override
  public @Nullable Icon getIcon() {
    return WurbletIcons.FILE;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new WurbletSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    // the tags mark the two levels, which are painted over whole regions by
    // WurbletLevelBackgroundAnnotator and are therefore invisible to the syntax highlighter
    // this preview is built from
    return """
        <wl>@{include $currentDir/header.incl}@</wl>
        <wl>@{extends ModelWurblet}@</wl>
        <wl>@{comment
        ({\\@code \\@wurblet}) Generate code to define the column lengths.
        }@</wl>
        <wl>@[
          private void wurbel() throws ModelException {
            String lead = getOption("noif") == null ? "" : "public static final ";
            for (Attribute attr: getEntity().getAttributes())  {
        ]@</wl>
        <ol>
          /** maximum number of characters for '</ol><wl>@(attr)@</wl><ol>'. */
          </ol><wl>@(lead)@</wl><ol>int CL_</ol><wl>@(attr.getName().toUpperCase(Locale.ROOT))@</wl><ol> = </ol><wl>@(attr.getSize())@</wl><ol>;
        </ol><wl>@[
            }
          }
        ]@</wl>
        """;
  }

  @Override
  public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return Map.of("wl", WurbletSyntaxHighlighter.WURBLET_LEVEL_BACKGROUND,
                  "ol", WurbletSyntaxHighlighter.OUTPUT_LEVEL_BACKGROUND);
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public @NotNull String getDisplayName() {
    return "Wurblet";
  }
}
