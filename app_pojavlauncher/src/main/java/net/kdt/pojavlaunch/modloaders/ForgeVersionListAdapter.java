package net.kdt.pojavlaunch.modloaders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ForgeVersionListAdapter extends BaseExpandableListAdapter implements ExpandableListAdapter {
    private final List<String> mGameVersions;
    private final List<List<String>> mForgeVersions;
    private final LayoutInflater mLayoutInflater;

    public ForgeVersionListAdapter(List<String> forgeVersions, LayoutInflater layoutInflater) {
        this.mLayoutInflater = layoutInflater;
        mGameVersions = new ArrayList<>();
        mForgeVersions = new ArrayList<>();
        Collections.sort(forgeVersions, ForgeVersionListAdapter::compareVersionDesc);
        for(String version : forgeVersions) {
            int dashIndex = version.indexOf("-");
            String gameVersion = version.substring(0, dashIndex);
            List<String> versionList;
            int gameVersionIndex = mGameVersions.indexOf(gameVersion);
            if(gameVersionIndex != -1) versionList = mForgeVersions.get(gameVersionIndex);
            else {
                versionList = new ArrayList<>();
                mGameVersions.add(gameVersion);
                mForgeVersions.add(versionList);
            }
            versionList.add(version);
        }
    }

    private static int compareVersionDesc(String left, String right) {
        return compareVersion(right, left);
    }

    private static int compareVersion(String left, String right) {
        String[] leftParts = left.split("[^0-9]+");
        String[] rightParts = right.split("[^0-9]+");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int leftValue = i < leftParts.length && leftParts[i].length() > 0 ? Integer.parseInt(leftParts[i]) : 0;
            int rightValue = i < rightParts.length && rightParts[i].length() > 0 ? Integer.parseInt(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return left.compareTo(right);
    }

    @Override
    public int getGroupCount() {
        return mGameVersions.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return mForgeVersions.get(i).size();
    }

    @Override
    public Object getGroup(int i) {
        return getGameVersion(i);
    }

    @Override
    public Object getChild(int i, int i1) {
        return getForgeVersion(i, i1);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int i, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(R.layout.item_modloader_group, viewGroup, false);

        ((TextView) convertView.findViewById(R.id.modloader_group_title)).setText(getGameVersion(i));
        convertView.findViewById(R.id.modloader_group_arrow).setRotation(b ? 180f : 0f);

        return convertView;
    }

    @Override
    public View getChildView(int i, int i1, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(R.layout.item_modloader_child, viewGroup, false);
        ((TextView) convertView.findViewById(R.id.modloader_child_title)).setText(getForgeVersion(i, i1));
        return convertView;
    }

    private String getGameVersion(int i) {
        return mGameVersions.get(i);
    }

    private String getForgeVersion(int i, int i1){
        return mForgeVersions.get(i).get(i1);
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }
}
