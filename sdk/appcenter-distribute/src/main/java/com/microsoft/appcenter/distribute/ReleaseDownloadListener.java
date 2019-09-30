/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.distribute;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.microsoft.appcenter.distribute.download.ReleaseDownloader;
import com.microsoft.appcenter.utils.AppCenterLog;
import com.microsoft.appcenter.utils.HandlerUtils;
import com.microsoft.appcenter.utils.storage.SharedPreferencesManager;

import java.text.NumberFormat;
import java.util.Locale;

import static com.microsoft.appcenter.distribute.DistributeConstants.DOWNLOAD_STATE_ENQUEUED;
import static com.microsoft.appcenter.distribute.DistributeConstants.HANDLER_TOKEN_CHECK_PROGRESS;
import static com.microsoft.appcenter.distribute.DistributeConstants.KIBIBYTE_IN_BYTES;
import static com.microsoft.appcenter.distribute.DistributeConstants.LOG_TAG;
import static com.microsoft.appcenter.distribute.DistributeConstants.MEBIBYTE_IN_BYTES;
import static com.microsoft.appcenter.distribute.DistributeConstants.PREFERENCE_KEY_DOWNLOADED_DISTRIBUTION_GROUP_ID;
import static com.microsoft.appcenter.distribute.DistributeConstants.PREFERENCE_KEY_DOWNLOADED_RELEASE_HASH;
import static com.microsoft.appcenter.distribute.DistributeConstants.PREFERENCE_KEY_DOWNLOADED_RELEASE_ID;
import static com.microsoft.appcenter.distribute.DistributeConstants.PREFERENCE_KEY_DOWNLOAD_STATE;
import static com.microsoft.appcenter.distribute.DistributeConstants.PREFERENCE_KEY_DOWNLOAD_TIME;
import static com.microsoft.appcenter.distribute.InstallerUtils.getInstallIntent;

/**
 * Listener for downloading progress.
 */
class ReleaseDownloadListener implements ReleaseDownloader.Listener {

    /**
     * Context.
     */
    private final Context mContext;

    /**
     * Private field to store information about release we are currently working with.
     */
    private final ReleaseDetails mReleaseDetails;

    ReleaseDownloadListener(@NonNull Context context, @NonNull ReleaseDetails releaseDetails) {
        mContext = context;
        mReleaseDetails = releaseDetails;
    }

    /**
     * Last download progress dialog that was shown.
     * Android 8 deprecates this dialog but only reason is that they want us to use a non modal
     * progress indicator while we actually use it to be a modal dialog for forced update.
     * They will always keep this dialog to remain compatible but just mark it deprecated.
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private android.app.ProgressDialog mProgressDialog;

    @Override
    public void onStart(long enqueueTime) {
        AppCenterLog.debug(LOG_TAG, String.format(Locale.ENGLISH, "Start download %s (%d) update.",
                mReleaseDetails.getShortVersion(), mReleaseDetails.getVersion()));
        SharedPreferencesManager.putInt(PREFERENCE_KEY_DOWNLOAD_STATE, DOWNLOAD_STATE_ENQUEUED);
        SharedPreferencesManager.putLong(PREFERENCE_KEY_DOWNLOAD_TIME, enqueueTime);
    }

    @Override
    @WorkerThread
    public boolean onProgress(final long currentSize, final long totalSize) {
        AppCenterLog.verbose(LOG_TAG, String.format(Locale.ENGLISH, "Downloading %s (%d) update: %d KiB / %d KiB",
                mReleaseDetails.getShortVersion(), mReleaseDetails.getVersion(),
                currentSize / KIBIBYTE_IN_BYTES, totalSize / KIBIBYTE_IN_BYTES));

        /* If file size is known update downloadProgress bar. */
        if (mProgressDialog != null && totalSize >= 0) {
            HandlerUtils.runOnUiThread(new Runnable() {

                @Override
                @SuppressWarnings({"deprecation", "RedundantSuppression"})
                public void run() {

                    /* When we switch from indeterminate to determinate */
                    if (mProgressDialog.isIndeterminate()) {

                        /* Configure the progress dialog determinate style. */
                        mProgressDialog.setProgressPercentFormat(NumberFormat.getPercentInstance());
                        mProgressDialog.setProgressNumberFormat(mContext.getString(R.string.appcenter_distribute_download_progress_number_format));
                        mProgressDialog.setIndeterminate(false);
                        mProgressDialog.setMax((int) (totalSize / MEBIBYTE_IN_BYTES));
                    }
                    mProgressDialog.setProgress((int) (currentSize / MEBIBYTE_IN_BYTES));
                }
            });
            return true;
        }
        return false;
    }

    @Override
    @WorkerThread
    public boolean onComplete(@NonNull Uri localUri) {
        Intent intent = getInstallIntent(localUri);
        if (intent.resolveActivity(mContext.getPackageManager()) == null) {
            AppCenterLog.debug(LOG_TAG, "Cannot resolve install intent for " + localUri);
            return false;
        }
        AppCenterLog.debug(LOG_TAG, String.format(Locale.ENGLISH, "Download %s (%d) update completed.",
                mReleaseDetails.getShortVersion(), mReleaseDetails.getVersion()));

        /* Check if app should install now. */
        if (!Distribute.getInstance().notifyDownload(mReleaseDetails, intent)) {

            /*
             * This start call triggers strict mode in UI thread so it
             * needs to be done here without synchronizing
             * (not to block methods waiting on synchronized on UI thread)
             * so yes we could launch install and SDK being disabled.
             *
             * This corner case cannot be avoided without triggering
             * strict mode exception.
             */
            AppCenterLog.info(LOG_TAG, "Show install UI for " + localUri);
            mContext.startActivity(intent);
            Distribute distribute = Distribute.getInstance();
            if (mReleaseDetails.isMandatoryUpdate()) {
                distribute.setInstalling(mReleaseDetails);
            } else {
                distribute.completeWorkflow(mReleaseDetails);
            }
            storeReleaseDetails(mReleaseDetails);
        }
        return true;
    }

    @Override
    public void onError(@NonNull String errorMessage) {

        /*
         * TODO: Add a generic error message to resources (with translations) to show the toast.
         * We can’t show any not-translated messages on UI.
         * Toast.makeText(mContext, errorMessage, Toast.LENGTH_SHORT).show();
         */
        AppCenterLog.error(LOG_TAG, errorMessage);
        Distribute.getInstance().completeWorkflow(mReleaseDetails);
    }

    /**
     * Show download progress. Only used for mandatory updates.
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    android.app.ProgressDialog showDownloadProgress(Activity foregroundActivity) {
        if (!mReleaseDetails.isMandatoryUpdate()) {
            return null;
        }
        mProgressDialog = new android.app.ProgressDialog(foregroundActivity);
        mProgressDialog.setTitle(R.string.appcenter_distribute_downloading_mandatory_update);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressNumberFormat(null);
        mProgressDialog.setProgressPercentFormat(null);
        return mProgressDialog;
    }

    /**
     * Hide progress dialog and stop updating. Only used for mandatory updates.
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    synchronized void hideProgressDialog() {
        if (mProgressDialog != null) {
            final android.app.ProgressDialog progressDialog = mProgressDialog;
            mProgressDialog = null;

            /* This can be called from background check download task. */
            HandlerUtils.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    progressDialog.hide();
                }
            });
            HandlerUtils.getMainHandler().removeCallbacksAndMessages(HANDLER_TOKEN_CHECK_PROGRESS);
        }
    }

    private static void storeReleaseDetails(@NonNull ReleaseDetails releaseDetails) {
        String groupId = releaseDetails.getDistributionGroupId();
        String releaseHash = releaseDetails.getReleaseHash();
        int releaseId = releaseDetails.getId();
        AppCenterLog.debug(LOG_TAG, "Stored release details: group id=" + groupId + " release hash=" + releaseHash + " release id=" + releaseId);
        SharedPreferencesManager.putString(PREFERENCE_KEY_DOWNLOADED_DISTRIBUTION_GROUP_ID, groupId);
        SharedPreferencesManager.putString(PREFERENCE_KEY_DOWNLOADED_RELEASE_HASH, releaseHash);
        SharedPreferencesManager.putInt(PREFERENCE_KEY_DOWNLOADED_RELEASE_ID, releaseId);
    }
}
