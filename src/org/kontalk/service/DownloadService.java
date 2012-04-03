/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOADING;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_OK;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_ERROR;

import java.io.File;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.ClientHTTPConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.message.AbstractMessage;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.ui.ConversationList;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MediaStorage;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;


/**
 * The attachment download service.
 * TODO implement multiple downloads
 * @author Daniele Ricci
 */
public class DownloadService extends IntentService implements DownloadListener {
    private static final String TAG = DownloadService.class.getSimpleName();

    public static final String ACTION_DOWNLOAD_URL = "org.kontalk.action.DOWNLOAD_URL";

    private Notification mCurrentNotification;
    private long mTotalBytes;

    private String messageId;
    private ClientHTTPConnection mDownloadClient;

    public DownloadService() {
        super(DownloadService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // unknown action
        if (!ACTION_DOWNLOAD_URL.equals(intent.getAction())) return;

        if (mDownloadClient == null) {
            EndpointServer server = MessagingPreferences.getEndpointServer(this);
            String token = Authenticator.getDefaultAccountToken(this);
            mDownloadClient = new ClientHTTPConnection(null, this, server, token);
        }

        Uri uri = intent.getData();
        messageId = intent.getStringExtra(AbstractMessage.MSG_ID);

        try {
            // check if external storage is available
            if (!MediaStorage.isExternalStorageAvailable()) {
                errorNotification(getString(R.string.notify_ticker_external_storage),
                        getString(R.string.notify_text_external_storage));
                return;
            }

            // make sure storage directory is present
            MediaStorage.MEDIA_ROOT.mkdirs();

            // download content
            // TEST for testing in emulator...
            mDownloadClient.downloadAutofilename(
                    uri.toString().replaceFirst("localhost", "10.0.2.2"),
                    MediaStorage.MEDIA_ROOT, this);
            //mDownloadClient.downloadAutofilename(uri.toString(), MediaStorage.MEDIA_ROOT, this);
        }
        catch (Exception e) {
            error(uri.toString(), null, e);
        }
    }

    public void startForeground(long totalBytes) {
        Log.d(TAG, "starting foreground progress notification");
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ConversationList.class);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOADING, ni, Intent.FLAG_ACTIVITY_NEW_TASK);

        mCurrentNotification = new Notification(R.drawable.icon_stat,
                getString(R.string.downloading_attachment), System.currentTimeMillis());
        mCurrentNotification.contentIntent = pi;
        mCurrentNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        foregroundNotification(0);
        startForeground(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);
    }

    private void foregroundNotification(int progress) {
        mCurrentNotification.contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.progress_notification);
        mCurrentNotification.contentView.setTextViewText(R.id.title, getString(R.string.downloading_attachment));
        mCurrentNotification.contentView.setTextViewText(R.id.progress_text, String.format("%d%%", progress));
        mCurrentNotification.contentView.setProgressBar(R.id.progress_bar, 100, progress, false);
    }

    public void stopForeground() {
        stopForeground(true);
        mCurrentNotification = null;
        mTotalBytes = 0;
    }

    @Override
    public void start(String url, File destination, long length) {
        startForeground(length);
    }

    @Override
    public void completed(String url, String mime, File destination) {
        Log.d(TAG, "download complete");
        stopForeground();

        Uri uri = Uri.fromFile(destination);

        // detect mime type if not available
        if (mime == null)
            mime = getContentResolver().getType(uri);

        // create intent for download complete notification
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mime);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_OK, i, Intent.FLAG_ACTIVITY_NEW_TASK);

        // create notification
        Notification no = new Notification(R.drawable.icon_stat,
                getString(R.string.notify_ticker_download_completed),
                System.currentTimeMillis());
        no.setLatestEventInfo(getApplicationContext(),
                getString(R.string.notify_title_download_completed),
                getString(R.string.notify_text_download_completed), pi);
        no.flags |= Notification.FLAG_AUTO_CANCEL;

        // notify!!
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID_DOWNLOAD_OK, no);

        // update messages.localUri
        ContentValues values = new ContentValues();
        values.put(Messages.LOCAL_URI, uri.toString());
        values.put(Messages.FETCHED, true);
        getContentResolver().update(Messages.getUri(messageId), values, null, null);
    }

    @Override
    public void error(String url, File destination, Throwable exc) {
        Log.e(TAG, "download error", exc);

        errorNotification(getString(R.string.notify_ticker_download_error),
                getString(R.string.notify_text_download_error));
    }

    private void errorNotification(String ticker, String text) {
        // create intent for download error notification
        Intent i = new Intent(this, ConversationList.class);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_ERROR, i, Intent.FLAG_ACTIVITY_NEW_TASK);

        // create notification
        Notification no = new Notification(R.drawable.icon_stat,
                ticker,
                System.currentTimeMillis());
        no.setLatestEventInfo(getApplicationContext(),
                getString(R.string.notify_title_download_error),
                text, pi);
        no.flags |= Notification.FLAG_AUTO_CANCEL;

        // notify!!
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID_DOWNLOAD_ERROR, no);
    }

    @Override
    public void progress(String url, File destination, long bytes) {
        //Log.v(TAG, bytes + " bytes received");
        if (mCurrentNotification != null) {
            int progress = (int)((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);
        }

        Thread.yield();
    }

}
