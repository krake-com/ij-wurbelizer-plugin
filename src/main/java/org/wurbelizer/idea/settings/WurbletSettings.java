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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Project level settings of the wurblet plugin.
 */
@Service(Service.Level.PROJECT)
@State(name = "WurbletSettings", storages = @Storage("wurblet.xml"))
public final class WurbletSettings implements PersistentStateComponent<WurbletSettings> {

  /** How the output level is wrapped for the Java injection. */
  public enum OutputWrapper {
    /** Decide per file by parsing both ways and taking the one with fewer errors. */
    AUTO,
    /** Always wrap the output level as a class body. */
    CLASS_BODY,
    /** Always wrap the output level as a list of statements. */
    STATEMENTS,
  }

  /** Whether Java is injected into the output level at all. */
  public boolean injectOutputLevel = true;

  /** How the output level is wrapped. */
  public OutputWrapper outputWrapper = OutputWrapper.AUTO;

  /**
   * Whether the output level is treated as Java when the parent class cannot be resolved.
   * <p>
   * When the parent class resolves, the output level is treated as Java exactly if the parent
   * is {@code org.wurbelizer.wurblet.AbstractJavaWurblet} or a subclass of it.
   */
  public boolean assumeJavaWhenParentUnresolved = true;

  /**
   * Additional variables for {@code @{include}@} paths.
   * <p>
   * {@code $currentDir} and {@code $currentFile} are always provided.
   */
  public Map<String, String> variables = new LinkedHashMap<>();

  /**
   * Returns the settings of a project.
   *
   * @param project the project
   * @return the settings, never null
   */
  public static @NotNull WurbletSettings getInstance(@NotNull Project project) {
    return project.getService(WurbletSettings.class);
  }

  @Override
  public @Nullable WurbletSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull WurbletSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
