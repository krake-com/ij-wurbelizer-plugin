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

package org.wurbelizer.idea.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings page of the wurblet plugin.
 */
public class WurbletConfigurable implements Configurable {

  private final Project project;

  private JCheckBox injectOutputLevel;
  private JCheckBox assumeJava;
  private JComboBox<WurbletSettings.OutputWrapper> outputWrapper;
  private JTextArea variables;

  /**
   * Creates the settings page.
   *
   * @param project the project
   */
  public WurbletConfigurable(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
    return "Wurblet";
  }

  @Override
  public @Nullable JComponent createComponent() {
    injectOutputLevel = new JCheckBox("Inject Java into the output level");
    assumeJava = new JCheckBox("Assume Java output when the parent class cannot be resolved");
    outputWrapper = new JComboBox<>(WurbletSettings.OutputWrapper.values());
    variables = new JTextArea(6, 40);
    variables.setBorder(BorderFactory.createEtchedBorder());

    JPanel wrapperPanel = new JPanel(new BorderLayout(8, 0));
    wrapperPanel.add(new JLabel("Output level wrapper:"), BorderLayout.WEST);
    wrapperPanel.add(outputWrapper, BorderLayout.CENTER);

    JPanel variablePanel = new JPanel(new BorderLayout(0, 4));
    variablePanel.add(new JLabel("Additional $-variables for @{include}@ paths, one 'name=value' per line:"),
                      BorderLayout.NORTH);
    variablePanel.add(new JScrollPane(variables), BorderLayout.CENTER);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(injectOutputLevel);
    panel.add(assumeJava);
    panel.add(Box.createVerticalStrut(8));
    panel.add(wrapperPanel);
    panel.add(Box.createVerticalStrut(8));
    panel.add(variablePanel);

    JPanel outer = new JPanel(new BorderLayout());
    outer.add(panel, BorderLayout.NORTH);
    reset();
    return outer;
  }

  @Override
  public boolean isModified() {
    WurbletSettings settings = WurbletSettings.getInstance(project);
    return injectOutputLevel.isSelected() != settings.injectOutputLevel
           || assumeJava.isSelected() != settings.assumeJavaWhenParentUnresolved
           || outputWrapper.getSelectedItem() != settings.outputWrapper
           || !parseVariables().equals(settings.variables);
  }

  @Override
  public void apply() {
    WurbletSettings settings = WurbletSettings.getInstance(project);
    settings.injectOutputLevel = injectOutputLevel.isSelected();
    settings.assumeJavaWhenParentUnresolved = assumeJava.isSelected();
    settings.outputWrapper = (WurbletSettings.OutputWrapper) outputWrapper.getSelectedItem();
    settings.variables = parseVariables();
  }

  @Override
  public void reset() {
    WurbletSettings settings = WurbletSettings.getInstance(project);
    injectOutputLevel.setSelected(settings.injectOutputLevel);
    assumeJava.setSelected(settings.assumeJavaWhenParentUnresolved);
    outputWrapper.setSelectedItem(settings.outputWrapper);
    StringBuilder buf = new StringBuilder();
    settings.variables.forEach((key, value) -> buf.append(key).append('=').append(value).append('\n'));
    variables.setText(buf.toString());
  }

  private Map<String, String> parseVariables() {
    Map<String, String> map = new LinkedHashMap<>();
    for (String line : variables.getText().split("\n")) {
      String trimmed = line.trim();
      int ndx = trimmed.indexOf('=');
      if (ndx > 0) {
        map.put(trimmed.substring(0, ndx).trim(), trimmed.substring(ndx + 1).trim());
      }
    }
    return map;
  }
}
