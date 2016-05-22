/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.github;

import java.util.List;
import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;
import org.kohsuke.github.GHCommitState;
import org.sonar.api.issue.Issue;
import org.sonar.api.rule.Severity;

public class GlobalReport {
  private final MarkDownUtils markDownUtils;
  private final boolean tryReportIssuesInline;
  private int[] newIssuesBySeverity = new int[Severity.ALL.size()];
  private StringBuilder notReportedOnDiff = new StringBuilder();
  private int extraIssueCount = 0;
  private int maxGlobalReportedIssues;
  private List<String> failingSeverities;

  public GlobalReport(MarkDownUtils markDownUtils, boolean tryReportIssuesInline, String severity) {
    this(markDownUtils, tryReportIssuesInline, severity, GitHubPluginConfiguration.MAX_GLOBAL_ISSUES);
  }

  @VisibleForTesting
  public GlobalReport(MarkDownUtils markDownUtils, boolean tryReportIssuesInline, String severity, int maxGlobalReportedIssues) {

    int i = Severity.ALL.indexOf(severity);
    if (i < 0) {
      throw new IllegalStateException("Severity level: " + severity + " is not an issue level.");
    }
    this.failingSeverities = Severity.ALL.subList(i, Severity.ALL.size());

    this.markDownUtils = markDownUtils;
    this.tryReportIssuesInline = tryReportIssuesInline;
    this.maxGlobalReportedIssues = maxGlobalReportedIssues;
  }

  private void increment(String severity) {
    this.newIssuesBySeverity[Severity.ALL.indexOf(severity)]++;
  }

  public String formatForMarkdown() {
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues == 0) {
      return "SonarQube analysis reported no issues.";
    }
    StringBuilder sb = new StringBuilder();
    boolean hasInlineIssues = newIssues > extraIssueCount;
    boolean extraIssuesTruncated = extraIssueCount > maxGlobalReportedIssues;
    sb.append("SonarQube analysis reported ").append(newIssues).append(" issue").append(newIssues > 1 ? "s" : "").append("\n");
    if (hasInlineIssues || extraIssuesTruncated) {
      printSummaryBySeverityMarkdown(sb);
    }
    if (tryReportIssuesInline && hasInlineIssues) {
      sb.append("\nWatch the comments in this conversation to review them.\n");
    }

    if (extraIssueCount > 0) {
      if (tryReportIssuesInline) {
        if (hasInlineIssues || extraIssuesTruncated) {
          int extraCount;
          sb.append("\n#### ");
          if (extraIssueCount <= maxGlobalReportedIssues) {
            extraCount = extraIssueCount;
          } else {
            extraCount = maxGlobalReportedIssues;
            sb.append("Top ");
          }
          sb.append(extraCount).append(" extra issue").append(extraCount > 1 ? "s" : "").append("\n");
        }
        sb.append(
          "\nNote: The following issues were found on lines that were not modified in the pull request. Because these issues can't be reported as line comments, they are summarized here:\n");
      } else if (extraIssuesTruncated) {
        sb.append("\n#### Top ").append(maxGlobalReportedIssues).append(" issues\n");
      }
      // Need to add an extra line break for ordered list to be displayed properly
      sb.append('\n')
        .append(notReportedOnDiff.toString());
    }
    return sb.toString();
  }

  public String getStatusDescription() {
    StringBuilder sb = new StringBuilder();
    printNewIssuesInline(sb);
    return sb.toString();
  }

  public GHCommitState getStatus() {
    for ( String severity : failingSeverities) {
      if (newIssues(severity) > 0) {
        return GHCommitState.ERROR;
      }
    }
    return GHCommitState.SUCCESS;
  }

  private int newIssues(String s) {
    return newIssuesBySeverity[Severity.ALL.indexOf(s)];
  }

  private void printSummaryBySeverityMarkdown(StringBuilder sb) {
    printNewIssuesForMarkdown(sb, Severity.BLOCKER);
    printNewIssuesForMarkdown(sb, Severity.CRITICAL);
    printNewIssuesForMarkdown(sb, Severity.MAJOR);
    printNewIssuesForMarkdown(sb, Severity.MINOR);
    printNewIssuesForMarkdown(sb, Severity.INFO);
  }



  private void printNewIssuesInline(StringBuilder sb) {
    sb.append("SonarQube reported ");

    int newIssues = 0;
    for (String severity : Severity.ALL) {
      newIssues += newIssues(severity);
    }

    if (newIssues > 0) {
      sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(",");
      for (String severity : failingSeverities) {
        printNewIssuesInline(sb, severity);
      }
      if (sb.charAt(sb.length() - 1) == ',') {
        sb.append(" none above " + failingSeverities.get(1) + " level.");
      }
    } else {
      sb.append("no issues");
    }
  }

  private void printNewIssuesInline(StringBuilder sb, String severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      if (sb.charAt(sb.length() - 1) == ',') {
        sb.append(" with ");
      } else {
        sb.append(" and ");
      }
      sb.append(issueCount).append(" ").append(severity.toLowerCase());
    }
  }

  private void printNewIssuesForMarkdown(StringBuilder sb, String severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      sb.append("* ").append(MarkDownUtils.getImageMarkdownForSeverity(severity)).append(" ").append(issueCount).append(" ").append(severity.toLowerCase()).append("\n");
    }
  }

  public void process(Issue issue, @Nullable String githubUrl, boolean reportedOnDiff) {
    increment(issue.severity());
    if (!reportedOnDiff) {
      if (extraIssueCount < maxGlobalReportedIssues) {
        notReportedOnDiff
          .append("1. ")
          .append(markDownUtils.globalIssue(issue.severity(), issue.message(), issue.ruleKey().toString(), githubUrl, issue.componentKey()))
          .append("\n");
      }
      extraIssueCount++;
    }
  }

  public boolean hasNewIssue() {
    return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
  }
}
