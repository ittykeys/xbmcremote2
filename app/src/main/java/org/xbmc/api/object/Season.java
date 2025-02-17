/*
 *      Copyright (C) 2005-2010 Team XBMC
 *      http://xbmc.org
 *
 *  This Program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2, or (at your option)
 *  any later version.
 *
 *  This Program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with XBMC Remote; see the file license.  If not, write to
 *  the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 *  http://www.gnu.org/copyleft/gpl.html
 *
 */

package org.xbmc.api.object;

import org.xbmc.android.util.Crc32;
import org.xbmc.api.type.MediaType;

import java.io.Serializable;
import java.util.List;

public class Season implements Serializable, ICoverArt {

    private static final long serialVersionUID = -7652780720536304140L;
    public final int number;
    public final boolean watched;
    public final TvShow show;
    public final String artUrl;
    public List<Episode> episodes = null;

    public Season(int number, boolean watched, TvShow show, String artUrl) {
        this.number = number;
        this.watched = watched;
        this.show = show;
        this.artUrl = artUrl;
    }

    public String getThumbUrl() {
        return artUrl;
    }

    public long getCrc() {
        // FileItem.cpp(1185)
        // BGetCachedThumb("season"+seasonPath+GetLabel(),g_settings.GetVideoThumbFolder(),true);
        return Crc32.computeLowerCase(artUrl);
    }

    public int getFallbackCrc() {
        return 0;
    }

    public int getId() {
        // TODO Auto-generated method stub
        return show.getId() * 10000 + number;
    }

    public int getMediaType() {
        return MediaType.VIDEO_TVSEASON;
    }

    public String getShortName() {
        if (number > 0) {
            return "Season " + number;
        } else {
            return "Specials";
        }
    }

    public String getName() {
        return show.getName() + " " + getShortName();
    }

    public String getPath() {
        // TODO Auto-generated method stub
        return null;
    }

    public String toString() {
        return getName();
    }
}
