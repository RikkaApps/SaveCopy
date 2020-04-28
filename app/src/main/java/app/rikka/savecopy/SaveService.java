package app.rikka.savecopy;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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
import java.util.Locale;

public class SaveService extends IntentService {

    private static final String TAG = "SaveService";

    private static final String NOTIFICATION_CHANNEL_PROGRESS = "progress";
    private static final String NOTIFICATION_CHANNEL_RESULT = "result";
    private static final int NOTIFICATION_ID_PROGRESS = 1;

    private Handler mHandler;
    private NotificationManager mNotificationManager;

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

        mNotificationManager = getSystemService(NotificationManager.class);
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

    @Override
    protected void onHandleIntent(Intent intent) {
        onSave(intent);
    }

    private void onSave(Intent intent) {
        int[] id = new int[]{Integer.MIN_VALUE};
        try {
            doSave(intent, id);
        } catch (Throwable e) {
            Log.e(TAG, "save " + intent.getData(), e);

            Throwable cause = e.getCause() == null ? e : e.getCause();
            CharSequence notificationTitle = getString(R.string.notification_error_title);
            CharSequence notificationText = getString(R.string.notification_error_text) + "\n\n" + cause.getMessage();

            Notification.Builder builder = newNotificationBuilder(NOTIFICATION_CHANNEL_RESULT, notificationTitle, notificationText)
                    .setStyle(new Notification.BigTextStyle().bigText(notificationText));
            scheduleNotification(id[0], builder);
        }
    }

    private void doSave(Intent intent, int[] _id) throws IOException, SaveException {
        Context context = this;
        CharSequence notificationTitle, notificationText;
        Notification.Builder builder;
        Uri data = intent.getData();
        if (data == null) {
            throw new SaveException("data is null");
        }

        ContentResolver cr = context.getContentResolver();

        String displayName = "unknown-" + System.currentTimeMillis();
        long totalSize = -1;
        try (Cursor cursor = cr.query(data, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (displayNameIndex != -1) {
                    displayName = cursor.getString(displayNameIndex);
                }
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1) {
                    totalSize = cursor.getLong(sizeIndex);
                }
            }
        }
        Uri tableUri;
        if (Build.VERSION.SDK_INT >= 29) {
            tableUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        } else {
            tableUri = MediaStore.Files.getContentUri("external");
        }

        if (Build.VERSION.SDK_INT <= 29) {
            // before Android 11 (actually includes 11 DP2), MediaStore can't name the file correctly, find a name by ourselves

            String[] displayParts = FileUtils.spiltFileName(displayName);

            String[] projection = Build.VERSION.SDK_INT >= 29
                    ? new String[]{MediaStore.MediaColumns.DISPLAY_NAME}
                    : new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA};
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + " like ?";
            String[] selectionArgs = new String[]{displayParts[0] + "%"};

            List<String> existingNames = new ArrayList<>();
            try (Cursor cursor = cr.query(tableUri, projection, selection, selectionArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    int dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                    do {
                        String existingName;
                        if (dataIndex != -1) {
                            File file = new File(cursor.getString(dataIndex));
                            String parent = file.getParent();
                            if (parent == null || !parent.startsWith(Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_DOWNLOADS + File.separator)) {
                                continue;
                            }
                            existingName = file.getName();
                        } else if (displayNameIndex != -1) {
                            existingName = cursor.getString(displayNameIndex);
                        } else {
                            existingName = null;
                        }
                        if (existingName != null) {
                            String[] existingParts = FileUtils.spiltFileName(existingName);
                            boolean add = false;
                            if (existingName.equals(displayName)) {
                                add = true;
                            } else if (displayParts[1].equals(existingParts[1])) {
                                add = existingParts[0].matches(String.format("%s \\(\\d+\\)", displayParts[0].replaceAll("([\\\\+*?\\[\\](){}|.^$])", "\\\\$1")));
                            }
                            if (add) {
                                existingNames.add(existingName);
                            }
                        }
                    } while (cursor.moveToNext());
                }
            }
            if (!existingNames.isEmpty()) {
                int index = 1;
                while (existingNames.contains(displayName)) {
                    displayName = String.format(Locale.ENGLISH, "%s (%d)%s", displayParts[0], index++, displayParts[1]);
                }
            }
        }

        InputStream is = cr.openInputStream(data);
        if (is == null) {
            throw new SaveException("can't open data");
        }

        ContentValues values;
        values = new ContentValues();

        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, true);
        } else {
            File parent = new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.DATA, new File(parent, displayName).getPath());

            // on lower versions, if the folder doesn't exist, insert will fail
            // as we have storage permission, just manually create it

            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);

        Uri fileUri = cr.insert(tableUri, values);
        if (fileUri == null) {
            throw new SaveException("can't insert");
        }
        int id = fileUri.toString().hashCode();
        _id[0] = id;

        notificationTitle = Html.fromHtml(getString(R.string.notification_saving_title,
                String.format("<font face=\"sans-serif-medium\">%s</font>", displayName)));
        builder = newNotificationBuilder(NOTIFICATION_CHANNEL_PROGRESS, notificationTitle, null);
        builder.setProgress(100, 0, true);
        scheduleNotification(id, builder);

        OutputStream os = cr.openOutputStream(fileUri, "w");
        if (os == null) {
            throw new SaveException("can't open output");
        }

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

        Log.d(TAG, writeSize + "/" + totalSize);

        if (Build.VERSION.SDK_INT >= 29) {
            values = new ContentValues();
            values.put(MediaStore.Images.ImageColumns.IS_PENDING, false);
            cr.update(fileUri, values, null, null);
        }

        String newName = displayName;
        try (Cursor cursor = cr.query(fileUri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (displayNameIndex != -1) {
                    newName = cursor.getString(displayNameIndex);
                }
            }
        }

        notificationTitle = getString(R.string.notification_saved_title, displayName);
        notificationText = Html.fromHtml(getString(R.string.notification_saved_text,
                String.format("<font face=\"sans-serif-medium\">%s/%s</font>", Environment.DIRECTORY_DOWNLOADS, newName)));
        builder = newNotificationBuilder(NOTIFICATION_CHANNEL_RESULT, notificationTitle, notificationText)
                .setStyle(new Notification.BigTextStyle().bigText(notificationText));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH)
                    .setSound(Uri.EMPTY)
                    .setVibrate(new long[0]);
        }
        if (!"application/vnd.android.package-archive".equals(intent.getType())) {
            String type = intent.getType();
            Intent newIntent = new Intent(intent)
                    .setComponent(null)
                    .setPackage(null)
                    .setDataAndType(fileUri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            Intent openIntent = Intent.createChooser(newIntent, getString(R.string.open_with));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                openIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new ComponentName[]{ComponentName.createRelative(this, SaveActivity.class.getName())});
            }
            PendingIntent openPendingIntent = PendingIntent.getActivity(this, id, openIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification_open_24),
                    getString(R.string.notification_action_open),
                    openPendingIntent).build());
        }
        scheduleNotification(id, builder);
    }
}
