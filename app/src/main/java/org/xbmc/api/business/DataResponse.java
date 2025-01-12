/*
 *      Copyright (C) 2005-2009 Team XBMC
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

package org.xbmc.api.business;


/**
 * Basically contains two things:
 * <ul>
 *   <li>The callback code of a completed HTTP API command</li>
 *   <li>The result of the HTTP API command</li>
 * </ul>
 *
 * @param <T> Type of the API command's result
 * @author Team XBMC
 */
public class DataResponse<T> implements Runnable, Cloneable {
    public T value;
    public int cacheType;

    public void run() {
        // do nothing if not overloaded
    }

    /**
     * Executed before downloading large files. Overload and return false to
     * skip downloading, for instance when a list with covers is scrolling.
     *
     * @return
     */
    public boolean postCache() {
        return true;
    }
}