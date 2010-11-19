/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.socialwidget;

import com.android.contacts.R;
import com.android.contacts.util.ContactBadgeUtil;
import com.android.contacts.views.ContactLoader;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.QuickContact;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.RemoteViews;

public class SocialWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "SocialWidgetProvider";
    private static SparseArray<ContactLoader> sLoaders = new SparseArray<ContactLoader>();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Log.d(TAG, "onUpdate called for " + appWidgetId);
        }

        for (int appWidgetId : appWidgetIds) {
            loadWidgetData(context, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            ContactLoader loader = sLoaders.get(appWidgetId);
            if (loader != null) {
                Log.d(TAG, "Stopping loader for widget with id=" + appWidgetId);
                loader.stopLoading();
                sLoaders.delete(appWidgetId);
            }
        }
        SocialWidgetSettings.getInstance().remove(context, appWidgetIds);
    }

    public static void loadWidgetData(final Context context, final int widgetId) {
        final ContactLoader previousLoader = sLoaders.get(widgetId);

        if (previousLoader != null) {
            previousLoader.startLoading();
        } else {
            // Show that we are loading
            final AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
            final RemoteViews loadingViews =
                    new RemoteViews(context.getPackageName(), R.layout.social_widget);
            loadingViews.setTextViewText(R.id.name,
                    context.getString(R.string.social_widget_loading));
            widgetManager.updateAppWidget(widgetId, loadingViews);

            // Load
            final Uri contactUri =
                    SocialWidgetSettings.getInstance().getContactUri(context, widgetId);
            if (contactUri == null) {
                // Not yet set-up (this can happen while the Configuration activity is visible)
                return;
            }
            final ContactLoader contactLoader = new ContactLoader(context, contactUri);
            contactLoader.registerListener(0,
                    new ContactLoader.OnLoadCompleteListener<ContactLoader.Result>() {
                        @Override
                        public void onLoadComplete(Loader<ContactLoader.Result> loader,
                                ContactLoader.Result contactData) {
                            if (contactData == ContactLoader.Result.ERROR ||
                                    contactData == ContactLoader.Result.NOT_FOUND) {
                                return;
                            }
                            Log.d(TAG, "Loaded " + contactData.getLookupKey()
                                    + " for widget with id=" + widgetId);
                            final RemoteViews views = new RemoteViews(context.getPackageName(),
                                    R.layout.social_widget);

                            setDisplayName(views, contactData.getDisplayName(),
                                    contactData.getPhoneticName());

                            byte[] photo = contactData.getPhotoBinaryData();
                            setPhoto(views, photo != null
                                    ? BitmapFactory.decodeByteArray(photo, 0, photo.length)
                                    : ContactBadgeUtil.loadPlaceholderPhoto(context));
                            setSocialSnippet(views, contactData.getSocialSnippet());
                            setStatusAttribution(views, ContactBadgeUtil.getSocialDate(
                                    contactData, context));

                            // OnClick launch QuickContact
                            final Intent intent = new Intent(QuickContact.ACTION_QUICK_CONTACT);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                            intent.setData(contactData.getLookupUri());
                            intent.putExtra(QuickContact.EXTRA_MODE, QuickContact.MODE_SMALL);

                            final PendingIntent pendingIntent = PendingIntent.getActivity(context,
                                    0, intent, 0);
                            views.setOnClickPendingIntent(R.id.border, pendingIntent);

                            // Configure Ui
                            widgetManager.updateAppWidget(widgetId, views);
                        }

                        private void setPhoto(RemoteViews views, Bitmap photo) {
                            views.setImageViewBitmap(R.id.image, photo);
                        }

                        /**
                         * Set the display name and phonetic name to show in the header.
                         */
                        private void setDisplayName(RemoteViews views, CharSequence displayName,
                                CharSequence phoneticName) {
                            if (TextUtils.isEmpty(phoneticName)) {
                                views.setTextViewText(R.id.name, displayName);
                            } else {
                                final String combinedName =
                                        context.getString(R.string.widget_name_and_phonetic,
                                        displayName, phoneticName);
                                views.setTextViewText(R.id.name, combinedName);
                            }
                        }

                        /**
                         * Set the social snippet text to display in the header.
                         */
                        private void setSocialSnippet(RemoteViews views, CharSequence snippet) {
                            if (TextUtils.isEmpty(snippet)) {
                                views.setViewVisibility(R.id.status, View.GONE);
                            } else {
                                views.setTextViewText(R.id.status, snippet);
                                views.setViewVisibility(R.id.status, View.VISIBLE);
                            }
                        }

                        /**
                         * Set the status attribution text to display in the header.
                         */
                        private void setStatusAttribution(RemoteViews views,
                                CharSequence attribution) {
                            if (attribution == null) {
                                views.setViewVisibility(R.id.status_date, View.GONE);
                            } else {
                                views.setTextViewText(R.id.status_date, attribution);
                                views.setViewVisibility(R.id.status_date, View.VISIBLE);
                            }
                        }
                    });
            contactLoader.startLoading();
            sLoaders.append(widgetId, contactLoader);
        }
    }
}
