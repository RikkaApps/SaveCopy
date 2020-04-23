package app.rikka.savecopy;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Html;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SaveService extends IntentService {

    private static final String NOTIFICATION_CHANNEL_PROGRESS = "progress";
    private static final String NOTIFICATION_CHANNEL_RESULT = "result";
    private static final int NOTIFICATION_ID_PROGRESS = 1;

    private Handler mHandler;
    private NotificationManager mNotificationManager;
    private Map<Integer, Notification> m;

    public SaveService() {
        super("save-thread");
    }

    public SaveService(String name) {
        super("save-thread");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler(Looper.getMainLooper(), msg -> {
            if (msg.obj instanceof Notification.Builder) {
                mNotificationManager.notify(msg.what, ((Notification.Builder) msg.obj).build());
                return true;
            }
            return false;
        });

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //noinspection ConstantConditions
        onCreateNotificationChannel(mNotificationManager);

        startForeground(NOTIFICATION_ID_PROGRESS, onStartForeground());
    }

    public Notification onStartForeground() {
        Notification.Builder builder = newNotificationBuilder(NOTIFICATION_CHANNEL_PROGRESS, getString(R.string.notification_working_title), null);
        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.O)
    public void onCreateNotificationChannel(@NonNull NotificationManager nm) {
        List<NotificationChannel> channels = new ArrayList<>();
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_PROGRESS,
                getString(R.string.notification_channel_progress),
                NotificationManager.IMPORTANCE_MIN);
        channel.setSound(null, null);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setBypassDnd(true);
        channel.setShowBadge(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            channel.setAllowBubbles(false);
        }
        channels.add(channel);

        channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_RESULT,
                getString(R.string.notification_channel_result),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setBypassDnd(true);
        channel.setShowBadge(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            channel.setAllowBubbles(false);
        }
        channels.add(channel);
        nm.createNotificationChannels(channels);
    }

    public Notification.Builder newNotificationBuilder(String channelId, CharSequence title, CharSequence text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, channelId) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification_24)
                .setColor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? getColor(R.color.notification) : getResources().getColor(R.color.notification));
        if (title != null) {
            builder.setContentTitle(title);
        }
        if (text != null) {
            builder.setContentText(text);
        }
        return builder;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        onSave(intent);
    }

    private void scheduleNotification(int id, Notification.Builder builder) {
        scheduleNotification(id, builder, 0);
    }

    private void scheduleNotification(int id, Notification.Builder builder, long delay) {
        if (delay == 0) {
            mHandler.removeMessages(id);
            mNotificationManager.notify(id, builder.build());
        } else {
            if (!mHandler.hasMessages(id)) {
                mHandler.sendMessageDelayed(Message.obtain(mHandler, id, builder), delay);
            }
        }
    }

    private void onSave(Intent intent) {
        try {
            doSave(intent);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void doSave(Intent intent) throws IOException, InterruptedException {
        Context context = this;
        CharSequence notificationTitle, notificationText;
        Notification.Builder builder;
        Uri data = intent.getData();
        if (data == null) {
            // TODO data is null
            return;
        }

        ContentResolver cr = context.getContentResolver();

        String displayName = "unknown-" + System.currentTimeMillis();
        long totalSize = -1;
        Cursor cursor = cr.query(data, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (displayNameIndex != -1) {
                displayName = cursor.getString(displayNameIndex);
            }
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (sizeIndex != -1) {
                totalSize = cursor.getLong(sizeIndex);
            }
            cursor.close();
        }

        InputStream is = cr.openInputStream(data);
        if (is == null) {
            // TODO can't open
            return;
        }

        ContentValues values;
        values = new ContentValues();

        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, true);
        } else {
            values.put(MediaStore.MediaColumns.DATA, Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_DOWNLOADS + File.separator + displayName);
        }
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);

        Uri target;
        if (Build.VERSION.SDK_INT >= 29) {
            target = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        } else {
            target = MediaStore.Files.getContentUri("external");
        }
        Uri uri = cr.insert(target, values);
        if (uri == null) {
            // TODO can't insert
            return;
        }

        int id = uri.toString().hashCode();
        notificationTitle = Html.fromHtml(getString(R.string.notification_saving_title,
                String.format("<font face=\"sans-serif-medium\">%s</font>", displayName)));
        builder = newNotificationBuilder(NOTIFICATION_CHANNEL_PROGRESS, notificationTitle, null);
        builder.setProgress(100, 0, true);
        scheduleNotification(id, builder);

        ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "rw");
        if (pfd == null) {
            // TODO can't open
            return;
        }

        OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
        long writeSize = 0;
        byte[] b = new byte[8192];
        for (int r; (r = is.read(b)) != -1; ) {
            os.write(b, 0, r);
            os.flush();

            writeSize += r;
            if (totalSize != -1) {
                int progress = (int) ((float) writeSize / totalSize * 100);
                builder.setProgress(100, progress, false);
                scheduleNotification(id, builder, 1000);
            }
        }
        os.close();
        is.close();
        Log.i("Save", writeSize + "/" + totalSize);

        if (Build.VERSION.SDK_INT >= 29) {
            values = new ContentValues();
            values.put(MediaStore.Images.ImageColumns.IS_PENDING, false);
            cr.update(uri, values, null, null);
        }

        notificationTitle = getString(R.string.notification_saved_title);
        notificationText = Html.fromHtml(getString(R.string.notification_saved_text,
                String.format("<font face=\"sans-serif-medium\">%s</font>", displayName),
                String.format("<font face=\"sans-serif-medium\">%s</font>", Environment.DIRECTORY_DOWNLOADS)));
        builder = newNotificationBuilder(NOTIFICATION_CHANNEL_RESULT, notificationTitle, notificationText)
                .setStyle(new Notification.BigTextStyle().bigText(notificationText));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH)
                    .setSound(Uri.EMPTY)
                    .setVibrate(new long[0]);
        }
        scheduleNotification(id, builder);
    }
}
