package com.leaf.hyperdragshare.gestures.xposed;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.leaf.hyperdragshare.compat.xposed.XposedHelpers;

/** Android user owning the mini task. A missing owner never defaults to SystemUI's user. */
final class MiniMediaUser {
    static int taskUserId(Object info) {
        try {
            Object task = XposedHelpers.callMethod(info, "getTaskInfo");
            return XposedHelpers.getIntField(task, "userId");
        } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
            return -1;
        }
    }

    // Xiaomi's PlaybackActivityMonitor writes the gain to a map keyed only by package name.
    // Even a write to the right player would change the gain inherited by its other installs.
    static int isolatedVolumeUid(Context context, String packageName, int taskUserId) {
        if (taskUserId < 0) return -1;
        try {
            UserManager users = context.getSystemService(UserManager.class);
            var installedUsers = (List<?>) UserManager.class.getMethod("getUserHandles", boolean.class)
                    .invoke(users, true);
            var packages = context.getPackageManager();
            Method getUid = PackageManager.class.getMethod("getPackageUidAsUser", String.class, int.class);
            Method getId = UserHandle.class.getMethod("getIdentifier");
            int targetUid = -1;
            for (Object user : installedUsers) {
                int id = (Integer) getId.invoke(user);
                int uid;
                try {
                    uid = (Integer) getUid.invoke(packages, packageName, id);
                } catch (InvocationTargetException error) {
                    if (error.getCause() instanceof PackageManager.NameNotFoundException) continue;
                    throw error;
                }
                if (id != taskUserId || !user.equals(UserHandle.getUserHandleForUid(uid))) return -1;
                targetUid = uid;
            }
            return targetUid;
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.e("Cannot confirm mini volume owner package=" + packageName + " taskUser=" + taskUserId, error);
            return -1;
        }
    }

    private MiniMediaUser() { }
}
