/*
 * Copyright (c) 2020 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom, Inc. - initial API and implementation
 *
 */

package org.eclipse.lsp.cobol.service;

import org.eclipse.lsp.cobol.jrpc.CobolLanguageClient;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.NonNull;
import lombok.Synchronized;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

/**
 * This class creates watchers with type to watch all types of events. The key to remove a watcher
 * is its path without any changes.
 */
@Singleton
public class WatcherServiceImpl implements WatcherService {

  /**
   * The kind of events of interest, for watchers calculated as WatchKind.Create | WatchKind.Change
   * | WatchKind.Delete which is 7
   */
  private static final int WATCH_ALL_KIND = 7;

  /** Glob patterns to watch the copybooks folder and copybook files */
  private static final String COPYBOOKS_FOLDER_GLOB = "**/.copybooks/**/*";

  private static final String WATCH_FILES = "workspace/didChangeWatchedFiles";
  private static final String WATCH_CONFIGURATION = "workspace/didChangeConfiguration";
  private static final String CONFIGURATION_CHANGE_ID = "configurationChange";
  private static final String PREDEFINED_FOLDER_WATCHER = "copybooksWatcher";
  public static final String FILE_BASENAME_VARIABLE = "${fileBasenameNoExtension}";

  private final List<String> folderWatchers = new ArrayList<>();
  private final Map<String, List<String>> runtimeSpecifiedFolderWatchers = new HashMap<>();

  private final Provider<CobolLanguageClient> clientProvider;

  @Inject
  WatcherServiceImpl(Provider<CobolLanguageClient> clientProvider) {
    this.clientProvider = clientProvider;
  }

  @NonNull
  public List<String> getWatchingFolders() {
    return unmodifiableList(folderWatchers);
  }

  @Override
  public void watchConfigurationChange() {
    register(singletonList(new Registration(CONFIGURATION_CHANGE_ID, WATCH_CONFIGURATION, null)));
  }

  @Override
  public void watchPredefinedFolder() {
    register(
        singletonList(
            new Registration(
                PREDEFINED_FOLDER_WATCHER,
                WATCH_FILES,
                new DidChangeWatchedFilesRegistrationOptions(
                    singletonList(
                        new FileSystemWatcher(
                            Either.forLeft(COPYBOOKS_FOLDER_GLOB), WATCH_ALL_KIND))))));
  }

  @Override
  @Synchronized
  public void addWatchers(@NonNull List<String> paths) {
    register(
        paths.stream()
            .map(
                folder -> {
                  folderWatchers.add(folder);
                  return new Registration(
                      folder,
                      WATCH_FILES,
                      new DidChangeWatchedFilesRegistrationOptions(
                          asList(
                              new FileSystemWatcher(createFileWatcher(folder), WATCH_ALL_KIND),
                              new FileSystemWatcher(createFolderWatcher(folder), WATCH_ALL_KIND))));
                })
            .collect(toList()));
  }

  /**
   * Watch all types of file system changes in folders with given paths relative to workspace folder
   *
   * @param paths - folders inside workspace to watch
   * @param documentUri - documents for which specified path need to be watched.
   */
  @Override
  public void addRuntimeWatchers(@NonNull List<String> paths, String documentUri) {
    List<String> watchedFolders =
        runtimeSpecifiedFolderWatchers.getOrDefault(documentUri, new ArrayList<>());
    watchedFolders.addAll(paths);
    runtimeSpecifiedFolderWatchers.put(documentUri, watchedFolders);
    register(
        watchedFolders.stream()
            .map(
                folder ->
                    new Registration(
                        folder,
                        WATCH_FILES,
                        new DidChangeWatchedFilesRegistrationOptions(
                            asList(
                                new FileSystemWatcher(createFileWatcher(folder), WATCH_ALL_KIND),
                                new FileSystemWatcher(
                                    createFolderWatcher(folder), WATCH_ALL_KIND)))))
            .collect(toList()));
  }

  @Override
  @Synchronized
  public void removeWatchers(@NonNull List<String> paths) {
    List<String> removedWatchers = paths.stream().filter(folderWatchers::remove).collect(toList());
    if (!removedWatchers.isEmpty()) {
      clientProvider
          .get()
          .unregisterCapability(
              new UnregistrationParams(
                  removedWatchers.stream()
                      .map(it -> new Unregistration(it, WATCH_FILES))
                      .collect(toList())));
    }
  }

  /**
   * Stop watching all types of file system changes specified for a document.
   *
   * @param documentUri - document for which runtime watchers need to be removed.
   */
  @Override
  public void removeRuntimeWatchers(@NonNull String documentUri) {
    List<String> removedWatchers =
        runtimeSpecifiedFolderWatchers.getOrDefault(documentUri, Collections.emptyList());
    runtimeSpecifiedFolderWatchers.remove(documentUri);
    if (!removedWatchers.isEmpty()) {
      clientProvider
          .get()
          .unregisterCapability(
              new UnregistrationParams(
                  removedWatchers.stream()
                      .map(it -> new Unregistration(it, WATCH_FILES))
                      .collect(toList())));
    }
  }

  private Either<String, RelativePattern> createFileWatcher(String folder) {
    return Either.forLeft("**/" + folder.replace(FILE_BASENAME_VARIABLE, "**") + "/**/*");
  }

  private Either<String, RelativePattern> createFolderWatcher(String folder) {
    return Either.forLeft("**/" + folder.replace(FILE_BASENAME_VARIABLE, "**"));
  }

  private void register(List<Registration> registrations) {
    clientProvider.get().registerCapability(new RegistrationParams(registrations));
  }
}
