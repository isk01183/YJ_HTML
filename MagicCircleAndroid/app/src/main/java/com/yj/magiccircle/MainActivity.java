package com.yj.magiccircle;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        root.addView(WebViews.magicCircle(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setPadding(24, 16, 24, 16);
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button settings = new Button(this);
        settings.setText("접근성 서비스 설정 열기");
        settings.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(isServiceEnabled()
                ? "충전 애니메이션: 켜짐"
                : "충전 애니메이션: 꺼짐\n목록이 비활성화되면 앱 정보 > 오른쪽 위 메뉴 > 제한된 설정 허용을 먼저 선택하세요.");
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ServiceInfo service = info.getResolveInfo().serviceInfo;
            if (getPackageName().equals(service.packageName)
                    && ChargingAccessibilityService.class.getName().equals(service.name)) return true;
        }
        return false;
    }
}
