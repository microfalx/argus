package net.microfalx.argus.report;

import lombok.*;

/**
 * The configuration for Mail service.
 */
@Getter
@With
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReportSettings {

    /**
     * Indicates whether the system report is enabled.
     */
    private boolean enabled;

    /**
     * Indicates whether the system report should be generated on boot.
     */
    private boolean onBoot;

    /**
     * Indicates whether the system report should be sent when a notice is detected.
     */
    private boolean onNotice;

    /**
     * The daily report daily schedule in CRON format.
     * <p>
     * The daily schedule is sent out every day at midnight.
     */
    private String dailySchedule = "0 0 0 * * ?";

    /**
     * The regular issues report schedule in CRON format.
     * <p>
     * A report when application has issues is sent at this time.
     */
    private String withIssuesSchedule = "0 0 0/6 ? * * *";

    /**
     * The critical report schedule in CRON format.
     * <p>
     * A report when application has critical issues is sent at this time.
     */
    private String withCriticalIssuesSchedule = "0 0/15 * ? * * *";

    /**
     * The threshold of issues less than critical to trigger the report.
     */
    private int highIssuesThreshold = 10;

    /**
     * The threshold of critical issues to trigger the report.
     */
    private int criticalIssuesThreshold = 1;

    /**
     * A comma-separated list of report recipients (email addresses).
     * <p>
     * If the list is empty, the report will be sent to all admins.
     */
    private String recipients;

    /**
     * The password used to encrypt the report attachment.
     * <p>
     * If empty, no encryption will be applied. While a default password is provided, it is strongly recommended to
     * change it to a secure value to ensure the confidentiality of the report data (the report might contain sensitive
     * information).
     */
    private String password = "WzeXYN1j4S675lLsL4Vy";

    /**
     * The name of the reporting system.
     * <p>
     * If the list is empty, the hostname will be used.
     */
    private String systemName;


}
