package com.dotmarketing.portlets.structure.action;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.dotcms.contenttype.business.ContentTypeApi;
import com.dotcms.contenttype.business.FieldApi;
import com.dotcms.contenttype.exception.NotFoundInDbException;
import com.dotcms.contenttype.model.field.FieldBuilder;
import com.dotcms.contenttype.model.type.ContentType;
import com.dotcms.contenttype.transform.contenttype.StructureTransformer;
import com.dotcms.contenttype.transform.field.LegacyFieldTransformer;
import com.dotcms.repackage.javax.portlet.ActionRequest;
import com.dotcms.repackage.javax.portlet.ActionResponse;
import com.dotcms.repackage.javax.portlet.PortletConfig;
import com.dotcms.repackage.org.apache.commons.beanutils.BeanUtils;
import com.dotcms.repackage.org.apache.struts.action.ActionForm;
import com.dotcms.repackage.org.apache.struts.action.ActionMapping;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.business.DotStateException;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portal.struts.DotPortletAction;
import com.dotmarketing.portlets.categories.business.CategoryAPI;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.structure.business.FieldAPI;
import com.dotmarketing.portlets.structure.model.Field;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.portlets.structure.struts.FieldForm;
import com.dotmarketing.quartz.job.DeleteFieldJob;
import com.dotmarketing.util.InodeUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.Validator;
import com.dotmarketing.util.WebKeys;
import com.liferay.portal.model.User;
import com.liferay.portal.util.Constants;
import com.liferay.portlet.ActionRequestImpl;
import com.liferay.util.servlet.SessionMessages;

public class EditFieldAction extends DotPortletAction {

	private CategoryAPI categoryAPI = APILocator.getCategoryAPI();
	private ContentletAPI conAPI = APILocator.getContentletAPI();
	private FieldApi fapi2 = APILocator.getFieldAPI2();
	private FieldAPI fAPI = APILocator.getFieldAPI();
	private ContentTypeApi tapi = APILocator.getContentTypeAPI2();
	public CategoryAPI getCategoryAPI() {
		return categoryAPI;
	}

	public void setCategoryAPI(CategoryAPI categoryAPI) {
		this.categoryAPI = categoryAPI;
	}

	public void processAction(ActionMapping mapping, ActionForm form, PortletConfig config, ActionRequest req,
			ActionResponse res) throws Exception {

		User user = _getUser(req);

		String cmd = req.getParameter(Constants.CMD);
		String referer = req.getParameter("referer");

		if ((referer != null) && (referer.length() != 0)) {
			referer = URLDecoder.decode(referer, "UTF-8");
		}

		// Retrieve the field in the request
		if ((cmd == null) || !cmd.equals("reorder")) {
			_retrieveField(form, req, res);
		}
		HibernateUtil.startTransaction();

		/*
		 * saving the field
		 */
		if ((cmd != null) && cmd.equals(Constants.ADD)) {
			try {
				Logger.debug(this, "Calling Add/Edit Method");
				FieldForm fieldForm = (FieldForm) form;
				Field field = (Field) req.getAttribute(WebKeys.Field.FIELD);
				
				if (InodeUtils.isSet(field.getInode())) {
					if (field.isFixed()
							|| (field.getFieldType().equals(Field.FieldType.LINE_DIVIDER.toString())
									|| field.getFieldType().equals(Field.FieldType.TAB_DIVIDER.toString())
									|| field.getFieldType().equals(Field.FieldType.CATEGORIES_TAB.toString())
									|| field.getFieldType().equals(Field.FieldType.PERMISSIONS_TAB.toString())
									|| field.getFieldType().equals(Field.FieldType.RELATIONSHIPS_TAB.toString())
									|| field.getFieldContentlet().equals(FieldAPI.ELEMENT_CONSTANT) || field
									.getFieldType().equals(Field.FieldType.HIDDEN.toString()))) {
						
						field.setFieldName(fieldForm.getFieldName());

						// This is what you can change on a fixed field
						if (field.isFixed()) {
							field.setHint(fieldForm.getHint());
							field.setDefaultValue(fieldForm.getDefaultValue());
							field.setSearchable(fieldForm.isSearchable());
							field.setListed(fieldForm.isListed());
							// field.setFieldName(fieldForm.getFieldName());
						}

						Structure structure = CacheLocator.getContentTypeCache().getStructureByInode(field.getStructureInode());

						if (((structure.getStructureType() == Structure.STRUCTURE_TYPE_CONTENT) && !fAPI
								.isElementConstant(field))
								|| ((structure.getStructureType() == Structure.STRUCTURE_TYPE_WIDGET) && fAPI
										.isElementConstant(field))
								|| ((structure.getStructureType() == Structure.STRUCTURE_TYPE_FILEASSET) && fAPI
										.isElementConstant(field))
                                || ((structure.getStructureType() == Structure.STRUCTURE_TYPE_HTMLPAGE) && fAPI
										.isElementConstant(field))
								|| ((structure.getStructureType() == Structure.STRUCTURE_TYPE_FORM) && fAPI
										.isElementConstant(field))) {
							field.setValues(fieldForm.getValues());
						}
						BeanUtils.copyProperties(fieldForm, field);
					}
				}
				
				if (Validator.validate(req, form, mapping)) {
					if (_saveField(fieldForm, req, res, user)) {
						_sendToReferral(req, res, referer);
						return;
					}
				}

			} catch (Exception ae) {
				_handleException(ae, req);
				return;
			}

		}
		/*
		 * If we are deleting the field, run the delete action and return to the
		 * list
		 *
		 */
		else if ((cmd != null) && cmd.equals(Constants.DELETE)) {
			try {
				Logger.debug(this, "Calling Delete Method");
				_deleteField(form, req, res);
			} catch (Exception ae) {
				_handleException(ae, req);
				return;
			}
			_sendToReferral(req, res, referer);
		} else if ((cmd != null) && cmd.equals("reorder")) {
			try {
				Logger.debug(this, "Calling reorder Method");
				_reorderFields(form, req, res);
			} catch (Exception ae) {
				_handleException(ae, req);
				return;
			}
			_sendToReferral(req, res, referer);
		}
		HibernateUtil.commitTransaction();
		_loadForm(form, req, res);
		setForward(req, "portlet.ext.structure.edit_field");
	}

	private void _retrieveField(ActionForm form, ActionRequest req, ActionResponse res) throws DotStateException, DotDataException {
		Field field = new Field();
		String inodeString = req.getParameter("inode");
		if (InodeUtils.isSet(inodeString)) {
			/*
			 * long inode = Long.parseLong(inodeString); if (inode != 0) { field =
			 * FieldFactory.getFieldByInode(inode); }
			 */
			if (InodeUtils.isSet(inodeString)) {
				try {
					field = new LegacyFieldTransformer(fapi2.find(inodeString)).asOldField();
				} catch (NotFoundInDbException e) {
					field = new Field();
				}
			} else {
				String structureInode = req.getParameter("structureInode");
				field.setStructureInode(structureInode);
			}
		} else {
			String structureInode = req.getParameter("structureInode");
			field.setStructureInode(structureInode);
		}

		if (field.isFixed()) {

			String message = "warning.object.isfixed";
			SessionMessages.add(req, "message", message);

		}

		req.setAttribute(WebKeys.Field.FIELD, field);
	}

	private boolean _saveField(FieldForm form, ActionRequest req, ActionResponse res, User user) {
		try {
			FieldForm fieldForm = (FieldForm) form;
			Field legacyield = (Field) req.getAttribute(WebKeys.Field.FIELD);
			
			ContentType type = tapi.find(legacyield.getStructureInode(), user);
			



			//http://jira.dotmarketing.net/browse/DOTCMS-5918
			HttpServletRequest httpReq = ((ActionRequestImpl) req).getHttpServletRequest();
			/*
			 * 
			 * moved to api/factory
			try {
			    _checkUserPermissions(structure, user, PERMISSION_PUBLISH);
			} catch (Exception ae) {
				if (ae.getMessage().equals(WebKeys.USER_PERMISSIONS_EXCEPTION)) {
					SessionMessages.add(httpReq, "message", "message.insufficient.permissions.to.save");
				}
				throw ae;
			}

			String dataType = fieldForm.getDataType();

			if (fieldForm.isListed()) {
				fieldForm.setIndexed(true);
			}

			if (fieldForm.isSearchable()) {
				fieldForm.setIndexed(true);
			}

			if (fieldForm.isUnique()) {
				fieldForm.setRequired(true);
				fieldForm.setIndexed(true);
			}
			 */
			BeanUtils.copyProperties(legacyield, fieldForm);

			
			
			
			
			
			//To validate values entered for decimal/number type check box field
			//http://jira.dotmarketing.net/browse/DOTCMS-5516

			
			// moved to FileUtils
			/**
			 * 
	
			if (field.getFieldType().equals(Field.FieldType.CHECKBOX.toString())){
				String values = fieldForm.getValues();
                String temp = values.replaceAll("\r\n","|");
                String[] tempVals = StringUtil.split(temp.trim(), "|");
                try {
    				if(dataType.equals(Field.DataType.FLOAT.toString())){
    					if(values.indexOf("\r\n") > -1) {
    						SessionMessages.add(req, "error", "message.structure.invaliddatatype");
    					    return false;
    					}

    					for(int i=1;i<tempVals.length;i+= 2){
    						Float.parseFloat(tempVals[i]);
    					}
    				}else if(dataType.equals(Field.DataType.INTEGER.toString())){
    					if(values.indexOf("\r\n") > -1) {
    						SessionMessages.add(req, "error", "message.structure.invaliddatatype");
    					    return false;
    					}

    					for(int i=1;i<tempVals.length;i+= 2){
    							Integer.parseInt(tempVals[i]);
    					}
    				}

				  }catch (Exception e) {
  			          String message = "message.structure.invaliddata";
				    SessionMessages.add(req, "error", message);
				    return false;
				 }
			}
		 */
			/*
			// check if is a new field to add at the botton of the structure
			// field list
			if (!InodeUtils.isSet(fieldForm.getInode())) {
				isNew = true;
				int sortOrder = 0;
				List<Field> fields = FieldsCache.getFieldsByStructureInode(structure.getInode());
				for (Field f : fields) {
					// http://jira.dotmarketing.net/browse/DOTCMS-3232
					if (f.getFieldType().equalsIgnoreCase(fieldForm.getFieldType())
							&& f.getFieldType().equalsIgnoreCase(Field.FieldType.HOST_OR_FOLDER.toString())) {
						SessionMessages.add(req, "error", "message.structure.duplicate.host_or_folder.field");
						return false;
					}
					if (f.getSortOrder() > sortOrder)
						sortOrder = f.getSortOrder();
				}
				field.setSortOrder(sortOrder + 1);
				field.setFixed(false);
				field.setReadOnly(false);

				String fieldVelocityName = VelocityUtil.convertToVelocityVariable(fieldForm.getFieldName(), false);
				int found = 0;
				if (VelocityUtil.isNotAllowedVelocityVariableName(fieldVelocityName)) {
					found++;
				}

				String velvar;
				for (Field f : fields) {
					velvar = f.getVelocityVarName();
					if (velvar != null) {
						if (fieldVelocityName.equals(velvar)) {
							found++;
						} else if (velvar.contains(fieldVelocityName)) {
							String number = velvar.substring(fieldVelocityName.length());
							if (RegEX.contains(number, "^[0-9]+$")) {
								found++;
							}
						}
					}
				}
				if (found > 0) {
					fieldVelocityName = fieldVelocityName + Integer.toString(found);
				}

				//http://jira.dotmarketing.net/browse/DOTCMS-5616
				if(!validateInternalFieldVelocityVarName(fieldVelocityName)){
					fieldVelocityName+="1";
				}

				field.setVelocityVarName(fieldVelocityName);
			}
			*/
			/*
			if (!field.isFixed() && !field.isReadOnly()) {
				// gets the data type from the contentlet: bool, date, text, etc

				String prevDataType = (field.getFieldContentlet() != null) ? field.getFieldContentlet().replaceAll(
						"[0-9]*", "") : "";

				if (field.getFieldType().equals("categories_tab") || field.getFieldType().equals("permissions_tab")
						|| field.getFieldType().equals("relationships_tab")) {

					List<Field> structureFields = FieldsCache.getFieldsByStructureInode(structure.getInode());
					for (Field f : structureFields) {
						if (f.getFieldType().equals("categories_tab") && field.getFieldType().equals("categories_tab")
								&& !f.getInode().equals(field.getInode())) {
							String message = "message.structure.duplicate.categories_tab";
							SessionMessages.add(req, "error", message);
							return false;

						} else if (f.getFieldType().equals("permissions_tab")
								&& field.getFieldType().equals("permissions_tab")
								&& !f.getInode().equals(field.getInode())) {
							String message = "message.structure.duplicate.permissions_tab";
							SessionMessages.add(req, "error", message);
							return false;

						} else if (f.getFieldType().equals("relationships_tab")
								&& field.getFieldType().equals("relationships_tab")
								&& !f.getInode().equals(field.getInode())) {
							String message = "message.structure.duplicate.relationships_tab";
							SessionMessages.add(req, "error", message);
							return false;

						}
					}

				}

				if (!(field.getFieldType().equals("host or folder") || field.getFieldType().equals("line_divider") || field.getFieldType().equals("tab_divider")
						|| field.getFieldType().equals("categories_tab")
						|| field.getFieldType().equals("permissions_tab") || field.getFieldType().equals(
						"relationships_tab"))
						&& !UtilMethods.isSet(fieldForm.getDataType())) {
					// it's either an image, file or link so there is no
					// datatype
					field.setFieldContentlet("");
				}

				if (!UtilMethods.isSet(fieldForm.getDataType())) {
					// it's either an image, file or link so there is no
					// datatype
					if (!field.getFieldType().equals("host or folder")){
							field.setFieldContentlet("");
						}

				} else if (!prevDataType.equals(fieldForm.getDataType())) {
					String fieldContentlet = FieldFactory.getNextAvaliableFieldNumber(dataType, field.getInode(), field
							.getStructureInode());
					if (fieldContentlet == null) {
						// didn't find any empty ones, so im throwing an error
						// to the user to select a new one
						String message = "message.structure.nodatatype";
						SessionMessages.add(req, "error", message);
						return false;
					}
					field.setFieldContentlet(fieldContentlet);
				}

				if (field.getFieldType().equalsIgnoreCase(Field.FieldType.CATEGORY.toString())) {
					field.setValues(req.getParameter("categories"));
					field.setIndexed(true);

					// validate if a field with the same category already exists
					List<Field> stFields = FieldsCache.getFieldsByStructureInode(field.getStructureInode());
					for (Field stField : stFields) {
						if(stField.getFieldType().equalsIgnoreCase(Field.FieldType.CATEGORY.toString())
								&& UtilMethods.isSet(stField.getValues())
								&& stField.getValues().equals(field.getValues())
								&& !stField.getInode().equals(field.getInode())) {
							SessionMessages.add(httpReq, "message", "message.category.existing.field");
							return false;
						}
					}

					if (UtilMethods.isSet(fieldForm.getDefaultValue())) {
						List<Category> selectedCategoriesList = new ArrayList<Category>();
						String[] selectedCategories = fieldForm.getDefaultValue().trim().split("\\|");
						for (String cat : selectedCategories) {
							selectedCategoriesList.add(categoryAPI.findByName(cat, user, false));
						}
						Category category = categoryAPI.find(req.getParameter("categories"), user, false);
						List<Category> childrenCategories = categoryAPI.getChildren(category, user, false);
						if (!childrenCategories.containsAll(selectedCategoriesList)) {
							String message = "error.invalid.child.category";
							SessionMessages.add(req, "error", message);
							return false;
						}
					}

				}

				if (field.getFieldType().equalsIgnoreCase(Field.FieldType.TAG.toString()) || field.isSearchable()) {
					field.setIndexed(true);
				}
			}

			if (fieldForm.getElement().equals(FieldAPI.ELEMENT_CONSTANT) || fieldForm.getFieldType().equals(FieldAPI.ELEMENT_CONSTANT)) {
				field.setFieldContentlet(FieldAPI.ELEMENT_CONSTANT);
				field.setValues(fieldForm.getValues());
			}

			boolean isUpdating = UtilMethods.isSet(field.getInode());
			// saves this field
			FieldFactory.saveField(field);
			*/
			if(true) {
				//ActivityLogger.logInfo(ActivityLogger.class, "Update Field Action", "User " + _getUser(req).getUserId() + "/" + _getUser(req).getFirstName() + " modified field " + field.getFieldName() + " to " + structure.getName()
					//    + " Structure.", HostUtil.hostNameUtil(req, _getUser(req)));
			} else {
			//	ActivityLogger.logInfo(ActivityLogger.class, "Save Field Action", "User " + _getUser(req).getUserId() + "/" + _getUser(req).getFirstName() + " added field " + field.getFieldName() + " to " + structure.getName()
					 //   + " Structure.", HostUtil.hostNameUtil(req, _getUser(req)));
			}
			/*
			FieldsCache.removeFields(structure);
			CacheLocator.getContentTypeCache().remove(structure);
			StructureServices.removeStructureFile(structure);
			StructureFactory.saveStructure(structure);

			FieldsCache.addFields(structure, structure.getFields());

			//Refreshing permissions
			PermissionAPI perAPI = APILocator.getPermissionAPI();
			if(field.getFieldType().equals("host or folder")) {
				perAPI.resetChildrenPermissionReferences(structure);
			}

		    //http://jira.dotmarketing.net/browse/DOTCMS-5178
			if(!isNew && ((!wasIndexed && fieldForm.isIndexed()) || (wasIndexed && !fieldForm.isIndexed()))){
			  // rebuild contentlets indexes
			  conAPI.reindex(structure);
			}

			if (fAPI.isElementConstant(field)) {
				ContentletServices.removeContentletFile(structure);
				ContentletMapServices.removeContentletMapFile(structure);
				conAPI.refresh(structure);
			}
*/
			String message = "message.structure.savefield";
			SessionMessages.add(req, "message", message);
			//AdminLogger.log(EditFieldAction.class, "_saveField","Added field " + field.getFieldName() + " to " + structure.getName() + " Structure.", user);
			return true;
		} catch (Exception ex) {
			Logger.error(EditFieldAction.class, ex.toString(), ex);
		}
		return false;
	}

	private void _loadForm(ActionForm form, ActionRequest req, ActionResponse res) {
		try {
			FieldForm fieldForm = (FieldForm) form;
			Field field = (Field) req.getAttribute(WebKeys.Field.FIELD);

			String structureInode = field.getStructureInode();
			structureInode = (InodeUtils.isSet(structureInode) ? structureInode : req.getParameter("structureInode"));

			field.setStructureInode(structureInode);
			BeanUtils.copyProperties(fieldForm, field);

			if (fAPI.isElementDivider(field)) {
				fieldForm.setElement(FieldAPI.ELEMENT_DIVIDER);
			} else if (fAPI.isElementdotCMSTab(field)) {
				fieldForm.setElement(FieldAPI.ELEMENT_TAB);
			} else if (fAPI.isElementConstant(field)) {
				fieldForm.setElement(FieldAPI.ELEMENT_CONSTANT);
			} else {
				fieldForm.setElement(FieldAPI.ELEMENT_FIELD);
			}

			List<String> values = new ArrayList<String>();
			List<String> names = new ArrayList<String>();
			fieldForm.setDataType(field.getDataType());
			fieldForm.setFreeContentletFieldsValue(values);
			fieldForm.setFreeContentletFieldsName(names);
		} catch (Exception ex) {
			Logger.warn(EditFieldAction.class, ex.toString(),ex);
		}
	}

	private void _deleteField(ActionForm form, ActionRequest req, ActionResponse res) throws DotStateException, DotSecurityException, DotDataException {
		Field field = (Field) req.getAttribute(WebKeys.Field.FIELD);
		User user = _getUser(req);

		Structure structure = new StructureTransformer(tapi.find(field.getStructureInode(), user)).asStructure();


		try {
			DeleteFieldJob.triggerDeleteFieldJob(structure, field, user);
		} catch(Exception e) {
			Logger.error(this, "Unable to trigger DeleteFieldJob", e);
			SessionMessages.add(req, "error", "message.structure.deletefield.error");
		}

		SessionMessages.add(req, "message", "message.structure.deletefield.async");

	}

	private void _reorderFields(ActionForm form, ActionRequest req, ActionResponse res) {
		try {
			User user = _getUser(req);
			Enumeration enumeration = req.getParameterNames();
			while (enumeration.hasMoreElements()) {
				String parameterName = (String) enumeration.nextElement();
				if (parameterName.indexOf("order_") != -1) {
					String parameterValue = req.getParameter(parameterName);
					String fieldInode = parameterName.substring(parameterName.indexOf("_") + 1);
					com.dotcms.contenttype.model.field.Field field = fapi2.find(fieldInode);

					FieldBuilder builder = FieldBuilder.builder(field);
					builder.sortOrder(Integer.parseInt(parameterValue));
					fapi2.save(field, user);

				}
			}
			
			//VirtualLinksCache.clearCache();
			String message = "message.structure.reorderfield";
			SessionMessages.add(req, "message", message);

			//AdminLogger.log(EditFieldAction.class, "_saveField", "Added field " + field.getFieldName() + " to " + structure.getName() + " Structure.", user);


		} catch (Exception ex) {
			Logger.error(EditFieldAction.class, ex.toString());
		}
	}

	private boolean validateInternalFieldVelocityVarName(String fieldVelVarName){

	   return !(FieldApi.RESERVED_FIELD_VARS.contains(fieldVelVarName));

	}

	public String hostNameUtil(ActionRequest req) {

		ActionRequestImpl reqImpl = (ActionRequestImpl) req;
		HttpServletRequest httpReq = reqImpl.getHttpServletRequest();
		HttpSession session = httpReq.getSession();

		String hostId = (String) session.getAttribute(com.dotmarketing.util.WebKeys.CMS_SELECTED_HOST_ID);

		Host h = null;
		try {
			h = APILocator.getHostAPI().find(hostId, _getUser(req), false);
		} catch (DotDataException e) {
			_handleException(e, req);
		} catch (DotSecurityException e) {
			_handleException(e, req);
		}

		return h.getTitle()!=null?h.getTitle():"default";

	}

}
