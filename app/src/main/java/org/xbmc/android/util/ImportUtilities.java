/*
 * Copyright (C) 2008 Romain Guy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xbmc.android.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.StatFs;

import org.xbmc.api.object.ICoverArt;
import org.xbmc.api.type.MediaType;
import org.xbmc.api.type.ThumbSize;
import org.xbmc.api.type.ThumbSize.Dimension;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public abstract class ImportUtilities {

    private static final String CACHE_DIRECTORY = "xbmc";
    private static final double MIN_FREE_SPACE = 3;

    public static File getCacheDirectory(String type, int size) {
        StringBuilder sb = new StringBuilder(CACHE_DIRECTORY);
        sb.append(type);
        sb.append(ThumbSize.getDir(size));
        return IOUtilities.getExternalFile(sb.toString());
    }

    public static File getCacheFile(String type, int size, String name) {
        StringBuilder sb = new StringBuilder(32);
        sb.append(CACHE_DIRECTORY);
        sb.append(type);
        sb.append(ThumbSize.getDir(size));
        sb.append("/");
        sb.append(name);
        return IOUtilities.getExternalFile(sb.toString());
    }

    public static Bitmap addCoverToCache(ICoverArt cover, Bitmap bitmap, int thumbSize) {
        Bitmap sizeToReturn = null;
        File cacheDirectory;
        final int mediaType = cover.getMediaType();
        for (int currentThumbSize : ThumbSize.values()) {
            // don't save big covers
            if (currentThumbSize == ThumbSize.BIG) {
                if (thumbSize == currentThumbSize) {
                    return bitmap;
                } else {
                    continue;
                }
            }

            try {
                cacheDirectory = ensureCache(MediaType.getArtFolder(mediaType), currentThumbSize);
            } catch (IOException e) {
                return null;
            }

            File coverFile = new File(cacheDirectory, Crc32.formatAsHexLowerCase(cover.getCrc()));
            FileOutputStream out = null;
            try {
                final Bitmap source = bitmap;

                // Centre crop and resize the image
                Dimension targetDim = ThumbSize.getTargetDimension(currentThumbSize, mediaType, source.getWidth(), source.getHeight());

                double targetAR = targetDim.x / targetDim.y;
                double sourceAR = source.getWidth() / source.getHeight();

                Bitmap cropped;
                if (sourceAR > targetAR) {
                    int boundTop = 0;
                    int boundLeft = (int) ((source.getWidth() - (source.getHeight() * targetAR)) / 2);
                    int boundBottom = source.getHeight();
                    int boundRight = (int) ((source.getWidth() + (source.getHeight() * targetAR)) / 2);
                    cropped = Bitmap.createBitmap(source, boundLeft, boundTop, boundRight, boundBottom);
                } else if (sourceAR < targetAR) {
                    int boundTop = (int) ((source.getHeight() - (source.getWidth() * targetAR)) / 2);
                    int boundLeft = 0;
                    int boundBottom = (int) ((source.getHeight() + (source.getWidth() * targetAR)) / 2);
                    int boundRight = source.getWidth();
                    cropped = Bitmap.createBitmap(source, boundLeft, boundTop, boundRight, boundBottom);
                } else {
                    cropped = source;
                }

                Bitmap resized = Bitmap.createScaledBitmap(cropped, targetDim.x, targetDim.y, true);

                resized.compress(Bitmap.CompressFormat.JPEG, 85, new FileOutputStream(coverFile));
                if (thumbSize == currentThumbSize) {
                    sizeToReturn = resized;
                }
            } catch (FileNotFoundException e) {
                return null;
            } finally {
                IOUtilities.closeStream(out);
            }
        }

        return sizeToReturn;
    }

    public static int calculateSampleSize(BitmapFactory.Options options, Dimension targetDimension) {
        if (targetDimension.x == 0 || targetDimension.y == 0) {
            return 1;
        }
        Boolean scaleByHeight = Math.abs(options.outHeight - targetDimension.y) >= Math.abs(options.outWidth - targetDimension.x);
        if (options.outHeight * options.outWidth * 2 >= 200 * 200 * 2) {
            // Load, scaling to smallest power of 2 that'll get it <= desired dimensions
            double sampleSize = scaleByHeight ? options.outHeight / targetDimension.y : options.outWidth / targetDimension.x;
            return (int) Math.pow(2d, Math.floor(Math.log(sampleSize) / Math.log(2d)));
        } else {
            return 1;
        }
    }

    /**
     * Returns number of free bytes on the SD card.
     *
     * @return Number of free bytes on the SD card.
     */
    public static long freeSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return availableBlocks * blockSize;
    }

    /**
     * Returns total size of SD card in bytes.
     *
     * @return Total size of SD card in bytes.
     */
    public static long totalSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return totalBlocks * blockSize;
    }

    public static String assertSdCard() {
        if (!android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            return "Your SD card is not mounted. You'll need it for caching thumbs.";
        }
        if (freePercentage() < MIN_FREE_SPACE) {
            return "You need to have more than " + MIN_FREE_SPACE + "% of free space on your SD card.";
        }
        return null;
    }


    /**
     * Returns free space in percent.
     *
     * @return Free space in percent.
     */
    public static double freePercentage() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long availableBlocks = stat.getAvailableBlocks();
        long totalBlocks = stat.getBlockCount();
        return (double) availableBlocks / (double) totalBlocks * 100;
    }

    private static File ensureCache(String type, int size) throws IOException {
        File cacheDirectory = getCacheDirectory(type, size);
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs();
            new File(cacheDirectory, ".nomedia").createNewFile();
        }
        return cacheDirectory;
    }

    public static void purgeCache() {
        final int size[] = ThumbSize.values();
        final int[] mediaTypes = MediaType.getTypes();
        for (int i = 0; i < mediaTypes.length; i++) {
            String folder = MediaType.getArtFolder(mediaTypes[i]);
            for (int j = 0; j < size.length; j++) {
                File cacheDirectory = getCacheDirectory(folder, size[j]);
                if (cacheDirectory.exists() && cacheDirectory.isDirectory()) {
                    for (File file : cacheDirectory.listFiles()) {
                        file.delete();
                    }
                }
            }
        }
    }
}
