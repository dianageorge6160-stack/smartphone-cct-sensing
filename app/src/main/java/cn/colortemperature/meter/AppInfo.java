package cn.colortemperature.meter;

import android.content.Context;

final class AppInfo {
    private AppInfo() {}
    static String version(Context context) {
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return version == null ? context.getString(R.string.unknown) : version;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return context.getString(R.string.unknown);
        }
    }
}
