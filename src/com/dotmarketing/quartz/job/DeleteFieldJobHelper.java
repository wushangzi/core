package com.dotmarketing.quartz.job;

import com.dotcms.notifications.bean.NotificationLevel;
import com.dotcms.notifications.bean.NotificationType;
import com.dotcms.notifications.business.NotificationAPI;
import com.dotmarketing.exception.DotDataException;
import com.liferay.portal.language.LanguageException;
import com.liferay.portal.language.LanguageUtil;

import java.io.Serializable;
import java.util.Locale;

/**
 * This is just a helper for DeleteFieldJob
 * @author jsanca
 */
public class DeleteFieldJobHelper implements Serializable {


    public static final DeleteFieldJobHelper INSTANCE = new DeleteFieldJobHelper();

    private DeleteFieldJobHelper () {}

    /**
     * Generates the notification for the start deleting field
     * @param notfAPI
     * @param userLocale
     * @param userId
     * @param velocityVarName
     * @param iFieldNode
     * @param iStructureNode  @throws LanguageException
     * @throws DotDataException
     */
    public final void generateNotificationStartDeleting(final NotificationAPI notfAPI,
                                                        final Locale userLocale,
                                                        final String userId,
                                                        final String velocityVarName,
                                                        final String iFieldNode,
                                                        final String iStructureNode) throws LanguageException, DotDataException {

        final Locale locale = (null != userLocale)?
                userLocale: Locale.getDefault();

        notfAPI.generateNotification(
                LanguageUtil.get(locale, "notification.deletefieldjob.delete.info.title"), // title = Delete Field
                LanguageUtil.get(locale,
                        "notification.deletefieldjob.startdelete.info.message",
                        velocityVarName, iFieldNode, iStructureNode), // message = Deletion of Field '{0}' has been started. Field Inode: {1}, Structure Inode: {2}
                null, // no actions
                NotificationLevel.INFO,
                NotificationType.GENERIC,
                userId
        );
    } // generateNotificationStartDeleting.


    /**
     * Generates the notification for the end deleting field
     * @param notfAPI
     * @param userLocale
     * @param userId
     * @param velocityVarName
     * @param iFieldNode
     * @param iStructureNode  @throws LanguageException
     * @throws DotDataException
     */
    public final void generateNotificationEndDeleting(final NotificationAPI notfAPI,
                                                        final Locale userLocale,
                                                        final String userId,
                                                        final String velocityVarName,
                                                        final String iFieldNode,
                                                        final String iStructureNode) throws LanguageException, DotDataException {

        final Locale locale = (null != userLocale)?
                userLocale: Locale.getDefault();

        notfAPI.generateNotification(
                LanguageUtil.get(locale, "notification.deletefieldjob.delete.info.title"), // title = Delete Field
                LanguageUtil.get(locale,
                        "notification.deletefieldjob.enddelete.info.message",
                        velocityVarName, iFieldNode, iStructureNode), // message = Field {0} was deleted succesfully. Field Inode: {1}, Structure Inode: {2}
                null, // no actions
                NotificationLevel.INFO,
                NotificationType.GENERIC,
                userId
        );
    } // generateNotificationEndDeleting.

    /**
     * Generates the notification for the unable delete
     * @param notfAPI
     * @param userLocale
     * @param userId
     * @param velocityVarName
     * @param iFieldNode
     * @param iStructureNode  @throws LanguageException
     * @throws DotDataException
     */
    public final void generateNotificationUnableDelete(final NotificationAPI notfAPI,
                                                      final Locale userLocale,
                                                      final String userId,
                                                      final String velocityVarName,
                                                      final String iFieldNode,
                                                      final String iStructureNode) throws LanguageException, DotDataException {

        final Locale locale = (null != userLocale)?
                userLocale: Locale.getDefault();

        notfAPI.generateNotification(
                LanguageUtil.get(locale, "notification.deletefieldjob.delete.info.title"), // title = Delete Field
                LanguageUtil.get(locale,
                        "notification.deletefieldjob.unabledelete.info.message",
                        velocityVarName, iFieldNode, iStructureNode), // message = Unable to delete field {0}. Field Inode: {1}, Structure Inode: {2}
                null, // no actions
                NotificationLevel.ERROR,
                NotificationType.GENERIC,
                userId
        );
    } // generateNotificationEndDeleting.

} // E:OF:DeleteFieldJobHelper.
