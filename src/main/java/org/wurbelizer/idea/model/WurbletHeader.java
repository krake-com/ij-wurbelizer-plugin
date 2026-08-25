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

package org.wurbelizer.idea.model;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wurbelizer.idea.psi.WurbletFile;
import org.wurbelizer.idea.settings.WurbletSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The class header the wurbiler derives from the directives of a wurblet source.
 * <p>
 * Includes are followed recursively, exactly as the wurbiler does, because the directives
 * that matter in practice ({@code package}, {@code import}, {@code extends}) usually live in
 * a shared {@code header.incl}.
 */
public final class WurbletHeader {

  /** The default parent class the wurbiler writes when no {@code @{extends}@} is given. */
  public static final String DEFAULT_PARENT_CLASS = "AbstractWurblet";

  /** The base class of all wurblets that generate Java code. */
  public static final String JAVA_WURBLET_CLASS = "org.wurbelizer.wurblet.AbstractJavaWurblet";

  private final String packageName;
  private final List<String> imports;
  private final String parentClass;
  private final List<String> interfaces;
  private final List<VirtualFile> includedFiles;

  private WurbletHeader(String packageName, List<String> imports, String parentClass,
                        List<String> interfaces, List<VirtualFile> includedFiles) {
    this.packageName = packageName;
    this.imports = List.copyOf(imports);
    this.parentClass = parentClass;
    this.interfaces = List.copyOf(interfaces);
    this.includedFiles = List.copyOf(includedFiles);
  }

  /**
   * Returns the header of a wurblet source.
   *
   * @param file the wurblet source
   * @return the header, never null
   */
  public static @NotNull WurbletHeader of(@NotNull WurbletFile file) {
    return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
        new Collector(file).collect(), PsiModificationTracker.MODIFICATION_COUNT));
  }

  /**
   * Returns the package of the generated wurblet class.
   *
   * @return the package name, null if none
   */
  public @Nullable String getPackageName() {
    return packageName;
  }

  /**
   * Returns the imports of the generated wurblet class.
   * <p>
   * The wurbiler adds no implicit imports whatsoever.
   *
   * @return the imports, never null
   */
  public @NotNull List<String> getImports() {
    return imports;
  }

  /**
   * Returns the parent class of the generated wurblet class.
   *
   * @return the parent class, {@value #DEFAULT_PARENT_CLASS} if no directive was given
   */
  public @NotNull String getParentClass() {
    return parentClass == null ? DEFAULT_PARENT_CLASS : parentClass;
  }

  /**
   * Returns whether an explicit {@code @{extends}@} was given.
   *
   * @return true if explicit
   */
  public boolean hasExplicitParentClass() {
    return parentClass != null;
  }

  /**
   * Returns the interfaces of the generated wurblet class.
   *
   * @return the interfaces, never null
   */
  public @NotNull List<String> getInterfaces() {
    return interfaces;
  }

  /**
   * Returns the files pulled in by {@code @{include}@}, transitively.
   *
   * @return the files, never null
   */
  public @NotNull List<VirtualFile> getIncludedFiles() {
    return includedFiles;
  }

  /**
   * Collects the header by walking the directives and following the includes.
   */
  private static final class Collector {

    private final WurbletFile file;
    private final Map<String, String> variables = new LinkedHashMap<>();
    private final VirtualFile baseDir;
    private final Set<String> visited = new LinkedHashSet<>();
    private final List<String> imports = new ArrayList<>();
    private final List<String> interfaces = new ArrayList<>();
    private final List<VirtualFile> includedFiles = new ArrayList<>();
    private String packageName;
    private String parentClass;

    private Collector(WurbletFile file) {
      this.file = file;
      VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
      this.baseDir = virtualFile == null ? null : virtualFile.getParent();
      if (baseDir != null) {
        variables.put(WurbletVariables.CURRENT_DIR, baseDir.getPath());
      }
      if (virtualFile != null) {
        variables.put(WurbletVariables.CURRENT_FILE, virtualFile.getName());
      }
      variables.putAll(WurbletSettings.getInstance(file.getProject()).variables);
    }

    private WurbletHeader collect() {
      scan(file.getText());
      return new WurbletHeader(packageName, imports, parentClass, interfaces, includedFiles);
    }

    private void scan(CharSequence text) {
      for (WurbletDirectiveScanner.Directive directive : WurbletDirectiveScanner.scan(text)) {
        WurbletDirectiveType type = WurbletDirectiveType.forName(directive.name());
        if (type == null) {
          continue;
        }
        switch (type) {
          case PACKAGE -> {
            if (packageName == null && !directive.arguments().isEmpty()) {
              packageName = firstToken(directive.arguments());
            }
          }
          case IMPORT -> {
            String imp = firstToken(directive.arguments());
            if (imp != null && !imports.contains(imp)) {
              imports.add(imp);
            }
          }
          case EXTENDS -> {
            if (parentClass == null && !directive.arguments().isEmpty()) {
              parentClass = firstToken(directive.arguments());
            }
          }
          case IMPLEMENTS -> {
            for (String iface : directive.arguments().split("\\s+")) {
              if (!iface.isEmpty() && !interfaces.contains(iface)) {
                interfaces.add(iface);
              }
            }
          }
          case INCLUDE -> include(directive.arguments());
          default -> { }
        }
      }
    }

    private void include(String rawPath) {
      String path = WurbletVariables.substitute(firstTokenOrEmpty(rawPath), variables);
      VirtualFile included = WurbletVariables.resolve(path, baseDir);
      if (included == null || included.isDirectory() || !visited.add(included.getPath())) {
        return;   // missing or circular, the annotator reports it
      }
      includedFiles.add(included);
      CharSequence text = textOf(included);
      if (text != null) {
        scan(text);
      }
    }

    /**
     * Reads an included file.
     * <p>
     * The document is preferred over the file on disk, so that an unsaved edit of a shared
     * {@code header.incl} takes effect immediately.
     *
     * @param file the file
     * @return the content, null if it cannot be read
     */
    private static CharSequence textOf(VirtualFile file) {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        return document.getCharsSequence();
      }
      try {
        return VfsUtilCore.loadText(file);
      }
      catch (IOException ex) {
        return null;   // unreadable, treated as if the include were missing
      }
    }

    private static String firstTokenOrEmpty(String text) {
      String token = firstToken(text);
      return token == null ? "" : token;
    }

    private static String firstToken(String text) {
      String trimmed = text.trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      int ndx = 0;
      while (ndx < trimmed.length() && !Character.isWhitespace(trimmed.charAt(ndx))) {
        ndx++;
      }
      return trimmed.substring(0, ndx);
    }
  }

}
