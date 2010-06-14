/*
 * Copyright (c) 2008, 2009, 2010 Denis Tulskiy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with this work.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.tulskiy.musique.gui.playlist.dnd;

import com.tulskiy.musique.db.DBMapper;
import com.tulskiy.musique.gui.dialogs.ProgressDialog;
import com.tulskiy.musique.gui.playlist.PlaylistTable;
import com.tulskiy.musique.playlist.Playlist;
import com.tulskiy.musique.playlist.Song;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Denis Tulskiy
 * Date: Jun 11, 2010
 */
@SuppressWarnings({"unchecked"})
public class PlaylistTransferHandler extends TransferHandler {
    private DataFlavor fileListFlavor;
    private static DBMapper<Song> songMapper = DBMapper.create(Song.class);
    private PlaylistTable table;

    public PlaylistTransferHandler(PlaylistTable table) {
        try {
            this.table = table;
            fileListFlavor = new DataFlavor("text/uri-list; class=java.lang.String");
        } catch (ClassNotFoundException ignored) {
        }
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        PlaylistTable table = (PlaylistTable) c;
        return new SongsSelection(table.getSelectedSongs());
    }

    @Override
    public boolean canImport(TransferSupport support) {
        boolean songListSupported = support.isDataFlavorSupported(SongsSelection.getFlavor());
        boolean fileListSupported = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        boolean urlListSupported = support.isDataFlavorSupported(fileListFlavor);

        return fileListSupported || urlListSupported || songListSupported;
    }

    private void addFiles(PlaylistTable table, java.util.List<File> files) {
        ProgressDialog dialog = new ProgressDialog(table.getParentFrame(), "Adding Files");
        dialog.addFiles(table.getPlaylist(), files);
        table.update();
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support))
            return false;

        Transferable t = support.getTransferable();
        List<Song> songs = null;
        List<File> files = null;

        try {
            if (support.isDataFlavorSupported(SongsSelection.getFlavor())) {
                songs = (List<Song>) t.getTransferData(SongsSelection.getFlavor());
            } else if (support.isDataFlavorSupported(fileListFlavor)) {
                String data = (String) t.getTransferData(fileListFlavor);
                files = new ArrayList<File>();
                for (String s : data.split("\n")) {
                    files.add(new File(new URI(s.trim())));
                }
            } else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
            }

            if (files != null) {
                addFiles(table, files);
                return true;
            }

            if (songs != null) {
                Playlist playlist = table.getPlaylist();
                int insertRow;

                if (support.isDrop()) {
                    JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
                    int index = dl.getRow();
                    if (index == playlist.size()) {
                        //corner case
                        insertRow = index;
                    } else {
                        insertRow = table.convertRowIndexToModel(index);
                    }

                    if (insertRow != -1) {
                        int toSubstract = 0;
                        for (Song song : songs) {
                            if (playlist.indexOf(song) < insertRow)
                                toSubstract++;
                        }

                        insertRow -= toSubstract;
                    }

                    playlist.removeAll(songs);
                } else {
                    insertRow = table.getSelectedRow();
                }

                if (insertRow == -1)
                    insertRow = playlist.size();

                playlist.addAll(insertRow, songs);
                table.setRowSelectionInterval(insertRow, insertRow + songs.size() - 1);
                songs.clear();
                table.update();
                return true;
            }

        } catch (UnsupportedFlavorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        System.out.println("export done " + action + "Move is " + MOVE);
        try {
            if (data.isDataFlavorSupported(SongsSelection.getFlavor())) {
                List<Song> songs = (List<Song>) data.getTransferData(SongsSelection.getFlavor());
                Playlist playlist = table.getPlaylist();

                if (action == MOVE) {
                    playlist.removeAll(songs);
                    table.update();
                } else if (action == COPY) {
                    ArrayList<Song> temp = new ArrayList<Song>();
                    for (Song song : songs) {
                        temp.add(songMapper.copyOf(song));
                    }
                    //switcherooo
                    songs.clear();
                    songs.addAll(temp);
                }
            }
        } catch (UnsupportedFlavorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}