/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package net.sourceforge.subsonic.ajax;

import net.sourceforge.subsonic.dao.MediaFileDao;
import net.sourceforge.subsonic.domain.MediaFile;
import net.sourceforge.subsonic.domain.Player;
import net.sourceforge.subsonic.domain.Playlist;
import net.sourceforge.subsonic.i18n.SubsonicLocaleResolver;
import net.sourceforge.subsonic.service.MediaFileService;
import net.sourceforge.subsonic.service.PlayerService;
import net.sourceforge.subsonic.service.SecurityService;
import net.sourceforge.subsonic.service.SettingsService;
import org.directwebremoting.WebContextFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Provides AJAX-enabled services for manipulating playlists.
 * This class is used by the DWR framework (http://getahead.ltd.uk/dwr/).
 *
 * @author Sindre Mehus
 */
public class PlaylistService {

    private MediaFileService mediaFileService;
    private SecurityService securityService;
    private net.sourceforge.subsonic.service.PlaylistService playlistService;
    private MediaFileDao mediaFileDao;
    private SettingsService settingsService;
    private PlayerService playerService;
    private SubsonicLocaleResolver localeResolver;

    public List<Playlist> getReadablePlaylists() {
        HttpServletRequest request = WebContextFactory.get().getHttpServletRequest();
        String username = securityService.getCurrentUsername(request);
        return playlistService.getReadablePlaylistsForUser(username);
    }

    public List<Playlist> getWritablePlaylists() {
        HttpServletRequest request = WebContextFactory.get().getHttpServletRequest();
        String username = securityService.getCurrentUsername(request);
        return playlistService.getWritablePlaylistsForUser(username);
    }

    public PlaylistInfo getPlaylist(int id) {
        HttpServletRequest request = WebContextFactory.get().getHttpServletRequest();

        Playlist playlist = playlistService.getPlaylist(id);
        List<MediaFile> files = playlistService.getFilesInPlaylist(id);

        String username = securityService.getCurrentUsername(request);
        mediaFileService.populateStarredDate(files, username);
        return new PlaylistInfo(playlist, createEntries(files));
    }

    public List<Playlist> createEmptyPlaylist() {
        HttpServletRequest request = WebContextFactory.get().getHttpServletRequest();
        Locale locale = localeResolver.resolveLocale(request);
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);

        Date now = new Date();
        Playlist playlist = new Playlist();
        playlist.setUsername(securityService.getCurrentUsername(request));
        playlist.setCreated(now);
        playlist.setChanged(now);
        playlist.setShared(false);
        playlist.setName(dateFormat.format(now));

        playlistService.createPlaylist(playlist);
        return getReadablePlaylists();
    }

    public void createPlaylistForPlayQueue() {
        HttpServletRequest request = WebContextFactory.get().getHttpServletRequest();
        HttpServletResponse response = WebContextFactory.get().getHttpServletResponse();
        Player player = playerService.getPlayer(request, response);
        Locale locale = localeResolver.resolveLocale(request);
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);

        Date now = new Date();
        Playlist playlist = new Playlist();
        playlist.setUsername(securityService.getCurrentUsername(request));
        playlist.setCreated(now);
        playlist.setChanged(now);
        playlist.setShared(false);
        playlist.setName(dateFormat.format(now));

        playlistService.createPlaylist(playlist);
        playlistService.setFilesInPlaylist(playlist.getId(), player.getPlayQueue().getFiles());
    }

    public void createPlaylistForStarredSongs() {
        HttpServletRequest request = WebContextFactory.get().getHttpServletRequest();
        Locale locale = localeResolver.resolveLocale(request);
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);

        Date now = new Date();
        Playlist playlist = new Playlist();
        String username = securityService.getCurrentUsername(request);
        playlist.setUsername(username);
        playlist.setCreated(now);
        playlist.setChanged(now);
        playlist.setShared(false);

        ResourceBundle bundle = ResourceBundle.getBundle("net.sourceforge.subsonic.i18n.ResourceBundle", locale);
        playlist.setName(bundle.getString("top.starred") + " " + dateFormat.format(now));

        playlistService.createPlaylist(playlist);
        List<MediaFile> songs = mediaFileDao.getStarredFiles(0, Integer.MAX_VALUE, username);
        playlistService.setFilesInPlaylist(playlist.getId(), songs);
    }

    public void appendToPlaylist(int playlistId, List<Integer> mediaFileIds) {
        List<MediaFile> files = playlistService.getFilesInPlaylist(playlistId);
        for (Integer mediaFileId : mediaFileIds) {
            MediaFile file = mediaFileService.getMediaFile(mediaFileId);
            if (file != null) {
                files.add(file);
            }
        }
        playlistService.setFilesInPlaylist(playlistId, files);
    }
    
    private List<PlaylistInfo.Entry> createEntries(List<MediaFile> files) {
        List<PlaylistInfo.Entry> result = new ArrayList<PlaylistInfo.Entry>();
        for (MediaFile file : files) {
            result.add(new PlaylistInfo.Entry(file.getId(), file.getTitle(), file.getArtist(), file.getAlbumName(),
                    file.getDurationString(), file.getStarredDate() != null));
        }

        return result;
    }

    public PlaylistInfo toggleStar(int id, int index) {
        HttpServletRequest request = WebContextFactory.get().getHttpServletRequest();
        String username = securityService.getCurrentUsername(request);
        List<MediaFile> files = playlistService.getFilesInPlaylist(id);
        MediaFile file = files.get(index);

        boolean starred = mediaFileDao.getMediaFileStarredDate(file.getId(), username) != null;
        if (starred) {
            mediaFileDao.unstarMediaFile(file.getId(), username);
        } else {
            mediaFileDao.starMediaFile(file.getId(), username);
        }
        return getPlaylist(id);
    }

    public PlaylistInfo remove(int id, int index) {
        List<MediaFile> files = playlistService.getFilesInPlaylist(id);
        files.remove(index);
        playlistService.setFilesInPlaylist(id, files);
        return getPlaylist(id);
    }

    public PlaylistInfo up(int id, int index) {
        List<MediaFile> files = playlistService.getFilesInPlaylist(id);
        if (index > 0) {
            MediaFile file = files.remove(index);
            files.add(index - 1, file);
            playlistService.setFilesInPlaylist(id, files);
        }
        return getPlaylist(id);
    }

    public PlaylistInfo down(int id, int index) {
        List<MediaFile> files = playlistService.getFilesInPlaylist(id);
        if (index < files.size() - 1) {
            MediaFile file = files.remove(index);
            files.add(index + 1, file);
            playlistService.setFilesInPlaylist(id, files);
        }
        return getPlaylist(id);
    }

    public void deletePlaylist(int id) {
        playlistService.deletePlaylist(id);
    }

    public PlaylistInfo updatePlaylist(int id, String name, String comment, boolean shared) {
        Playlist playlist = playlistService.getPlaylist(id);
        playlist.setName(name);
        playlist.setComment(comment);
        playlist.setShared(shared);
        playlistService.updatePlaylist(playlist);
        return getPlaylist(id);
    }

    public void setPlaylistService(net.sourceforge.subsonic.service.PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setMediaFileService(MediaFileService mediaFileService) {
        this.mediaFileService = mediaFileService;
    }

    public void setMediaFileDao(MediaFileDao mediaFileDao) {
        this.mediaFileDao = mediaFileDao;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setPlayerService(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void setLocaleResolver(SubsonicLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }
}