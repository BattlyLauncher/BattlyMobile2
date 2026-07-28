package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.ModCompatibilityAnalyzer;

import java.util.ArrayList;

public class ModCompatibilityFragment extends Fragment {
    public static final String TAG = "ModCompatibilityFragment";
    private static final String ARG_LOADER = "loader";
    private static final String ARG_SUMMARY = "summary";
    private static final String ARG_SOLUTION = "solution";
    private static final String ARG_CURRENT_MC = "current_mc";
    private static final String ARG_RECOMMENDED_MC = "recommended_mc";
    private static final String ARG_ISSUE_TITLES = "issue_titles";
    private static final String ARG_ISSUE_DETAILS = "issue_details";

    public ModCompatibilityFragment() {
        super(R.layout.fragment_mod_compatibility);
    }

    public static Bundle createArguments(ModCompatibilityAnalyzer.Analysis analysis) {
        Bundle arguments = new Bundle();
        arguments.putString(ARG_LOADER, analysis.loader);
        arguments.putString(ARG_SUMMARY, analysis.summary);
        arguments.putString(ARG_SOLUTION, analysis.solution);
        arguments.putString(ARG_CURRENT_MC, analysis.currentMinecraftVersion);
        arguments.putString(ARG_RECOMMENDED_MC, analysis.recommendedMinecraftVersion);

        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> details = new ArrayList<>();
        for (ModCompatibilityAnalyzer.Issue issue : analysis.issues) {
            String title = issue.modName;
            if (Tools.isValidString(issue.installedVersion)) {
                title += " " + issue.installedVersion;
            }
            titles.add(title);

            String dependency = Tools.isValidString(issue.dependencyName)
                    ? issue.dependencyName : issue.dependencyId;
            String detail = dependency + " " + issue.requirement;
            if (Tools.isValidString(issue.currentVersion)) {
                detail += " · " + issue.currentVersion;
            }
            details.add(detail);
        }
        arguments.putStringArrayList(ARG_ISSUE_TITLES, titles);
        arguments.putStringArrayList(ARG_ISSUE_DETAILS, details);
        return arguments;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle arguments = getArguments() == null ? Bundle.EMPTY : getArguments();
        String loader = arguments.getString(ARG_LOADER, getString(R.string.mod_compat_loader_unknown));
        String currentMinecraft = arguments.getString(ARG_CURRENT_MC, "");
        String recommendedMinecraft = arguments.getString(ARG_RECOMMENDED_MC, "");
        String summary = arguments.getString(ARG_SUMMARY, "");
        String solution = arguments.getString(ARG_SOLUTION, "");

        ((TextView) view.findViewById(R.id.mod_compatibility_loader))
                .setText(getString(R.string.mod_compat_loader, loader));

        TextView summaryView = view.findViewById(R.id.mod_compatibility_summary);
        TextView solutionView = view.findViewById(R.id.mod_compatibility_solution);
        if (Tools.isValidString(currentMinecraft) && Tools.isValidString(recommendedMinecraft)) {
            summaryView.setText(getString(R.string.mod_compat_minecraft_summary,
                    currentMinecraft, recommendedMinecraft));
            solutionView.setText(getString(R.string.mod_compat_minecraft_solution,
                    currentMinecraft, recommendedMinecraft));
        } else {
            summaryView.setText(summary);
            solutionView.setText(solution);
        }
        solutionView.setVisibility(Tools.isValidString(solutionView.getText().toString())
                ? View.VISIBLE : View.GONE);

        ArrayList<String> titles = arguments.getStringArrayList(ARG_ISSUE_TITLES);
        ArrayList<String> details = arguments.getStringArrayList(ARG_ISSUE_DETAILS);
        LinearLayout issueContainer = view.findViewById(R.id.mod_compatibility_issues);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        if (titles == null || titles.isEmpty()) {
            View empty = inflater.inflate(
                    R.layout.item_mod_compatibility_issue, issueContainer, false);
            ((TextView) empty.findViewById(R.id.mod_compatibility_issue_title))
                    .setText(R.string.mod_compat_no_details);
            empty.findViewById(R.id.mod_compatibility_issue_detail).setVisibility(View.GONE);
            issueContainer.addView(empty);
        } else {
            for (int i = 0; i < titles.size(); i++) {
                View issueView = inflater.inflate(
                        R.layout.item_mod_compatibility_issue, issueContainer, false);
                ((TextView) issueView.findViewById(R.id.mod_compatibility_issue_title))
                        .setText(titles.get(i));
                ((TextView) issueView.findViewById(R.id.mod_compatibility_issue_detail))
                        .setText(details != null && i < details.size() ? details.get(i) : "");
                issueContainer.addView(issueView);
            }
        }

        view.findViewById(R.id.mod_compatibility_back).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());
        view.findViewById(R.id.mod_compatibility_logs).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LogViewerFragment.class,
                        LogViewerFragment.TAG, null));
    }
}
