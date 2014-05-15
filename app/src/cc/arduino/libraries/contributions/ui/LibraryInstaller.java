/*
 * This file is part of Arduino.
 *
 * Copyright 2014 Arduino LLC (http://www.arduino.cc/)
 *
 * Arduino is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, you may use this file as part of a free software
 * library without restriction.  Specifically, if other files instantiate
 * templates or use macros or inline functions from this file, or you compile
 * this file and link it with other files to produce an executable, this
 * file does not by itself cause the resulting executable to be covered by
 * the GNU General Public License.  This exception does not however
 * invalidate any other reasons why the executable file might be covered by
 * the GNU General Public License.
 */
package cc.arduino.libraries.contributions.ui;

import static processing.app.I18n._;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import processing.app.helpers.FileUtils;
import cc.arduino.libraries.contributions.ContributedLibrary;
import cc.arduino.libraries.contributions.LibrariesIndexer;
import cc.arduino.packages.contributions.DownloadableContributionsDownloader;
import cc.arduino.utils.ArchiveExtractor;
import cc.arduino.utils.MultiStepProgress;
import cc.arduino.utils.Progress;

public class LibraryInstaller {

  private LibrariesIndexer indexer;
  private File stagingFolder;
  private DownloadableContributionsDownloader downloader;

  public LibraryInstaller(LibrariesIndexer _indexer) {
    indexer = _indexer;
    stagingFolder = _indexer.getStagingFolder();
    downloader = new DownloadableContributionsDownloader(stagingFolder) {
      @Override
      protected void onProgress(Progress progress) {
        LibraryInstaller.this.onProgress(progress);
      };
    };
  }

  public void updateIndex() throws Exception {
    final MultiStepProgress progress = new MultiStepProgress(2);

    // Step 1: Download index
    URL url = new URL("http://arduino.cc/library_index.json");
    File outputFile = indexer.getIndexFile();
    File tmpFile = new File(outputFile.getAbsolutePath() + ".tmp");
    try {
      downloader.download(url, tmpFile, progress,
                          _("Downloading libraries index..."));
    } catch (InterruptedException e) {
      // Download interrupted... just exit
      return;
    }
    progress.stepDone();

    // TODO: Check downloaded index

    // Replace old index with the updated one
    if (outputFile.exists())
      outputFile.delete();
    if (!tmpFile.renameTo(outputFile))
      throw new Exception(
          _("An error occurred while updating libraries index!"));

    // Step 2: Rescan index
    rescanLibraryIndex(progress);
  }

  public void install(ContributedLibrary lib) throws Exception {
    if (lib.isInstalled())
      throw new Exception(_("Library is already installed!"));

    final MultiStepProgress progress = new MultiStepProgress(3);

    // Step 1: Download library
    try {
      downloader.download(lib, progress, _("Downloading library."));
    } catch (InterruptedException e) {
      // Download interrupted... just exit
      return;
    }

    // TODO: Extract to temporary folders and move to the final destination only
    // once everything is successfully unpacked. If the operation fails remove
    // all the temporary folders and abort installation.

    // Step 2: Unpack library on the correct location
    progress.setStatus(_("Installing library..."));
    onProgress(progress);
    File destFolder = new File(indexer.getSketchbookLibrariesFolder(), lib.getName());
    destFolder.mkdirs();
    ArchiveExtractor.extract(lib.getDownloadedFile(), destFolder, 1);
    progress.stepDone();

    // Step 3: Rescan index
    rescanLibraryIndex(progress);
  }

  public void remove(ContributedLibrary lib) throws IOException {
    final MultiStepProgress progress = new MultiStepProgress(2);

    // Step 1: Remove library
    progress.setStatus(_("Removing library..."));
    onProgress(progress);
    FileUtils.recursiveDelete(lib.getInstalledFolder());
    progress.stepDone();

    // Step 2: Rescan index
    rescanLibraryIndex(progress);
  }

  private void rescanLibraryIndex(MultiStepProgress progress)
      throws IOException {
    progress.setStatus(_("Updating list of installed libraries"));
    onProgress(progress);
    indexer.rescanLibraries();
    progress.stepDone();
  }

  protected void onProgress(Progress progress) {
    // Empty
  }
}
