package org.xbmc.android.remote2.presentation.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;

import org.xbmc.android.remote2.presentation.activity.NowPlayingActivity;

public abstract class NotificationBuilder {
    protected final PendingIntent mIntent;
    protected final Context mContext;

    protected NotificationBuilder(Context context) {
        mContext = context;
        final Intent actintent = new Intent(mContext, NowPlayingActivity.class);
        actintent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        mIntent = PendingIntent.getActivity(mContext, 0, actintent, PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Return the richest NotificationBuilder that will work on this platform.
     */
    public static NotificationBuilder getInstance(Context context) {
        if (Integer.valueOf(Build.VERSION.SDK) >= Build.VERSION_CODES.JELLY_BEAN) {
            return new BigPictureNotificationBuilder(context);
        }

        if (Integer.valueOf(Build.VERSION.SDK) >= Build.VERSION_CODES.HONEYCOMB) {
            return new LargeIconNotificationBuilder(context);
        }

        return new NotificationBuilder(context) {
            @Override
            public Notification build(String title, String text, int icon, Bitmap thumb) {
                return null;
            }
        };
    }

    /**
     * Create a simple notification. Subclasses may take advantage of newer APIs.
     *
     * @param title
     * @param text
     * @param icon  The id of a drawable to be used as the small icon. Will display on all platforms.
     * @param thumb A bitmap representing the currently playing item. Ignored on lower API levels.
     * @return
     */
    public Notification buildNotification(String title, String text, int icon, Bitmap thumb) {
        Notification.Builder builder = new Notification.Builder(mContext)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon);

        if (thumb != null) {
            builder.setLargeIcon(thumb);
        }

        Notification notification = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            notification = builder.build();
        }
        return finalize(notification);
    }

    /**
     * Perform modifications to a notification that apply to all API levels. All definitions of
     * buildNotification should call this before returning.
     */
    protected Notification finalize(Notification notification) {
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    public abstract Notification build(String title, String text, int icon, Bitmap thumb);
}
