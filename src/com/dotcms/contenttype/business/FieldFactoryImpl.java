package com.dotcms.contenttype.business;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.dotcms.contenttype.business.sql.FieldSql;
import com.dotcms.contenttype.exception.DotDataValidationException;
import com.dotcms.contenttype.exception.NotFoundInDbException;
import com.dotcms.contenttype.exception.OverFieldLimitException;
import com.dotcms.contenttype.model.field.CategoryField;
import com.dotcms.contenttype.model.field.DataTypes;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.field.FieldBuilder;
import com.dotcms.contenttype.model.field.FieldVariable;
import com.dotcms.contenttype.model.field.HostFolderField;
import com.dotcms.contenttype.model.field.ImmutableFieldVariable;
import com.dotcms.contenttype.model.field.LegacyFieldTypes;
import com.dotcms.contenttype.model.field.OnePerContentType;
import com.dotcms.contenttype.model.field.TagField;
import com.dotcms.contenttype.model.type.ContentType;
import com.dotcms.contenttype.transform.field.DbFieldTransformer;
import com.dotcms.contenttype.transform.field.DbFieldVariableTransformer;
import com.dotcms.repackage.org.apache.commons.lang.time.DateUtils;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.LocalTransaction;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.VelocityUtil;

public class FieldFactoryImpl implements FieldFactory {

  final FieldSql sql;

  public FieldFactoryImpl() {
    sql = FieldSql.getInstance();
  }

  @Override
  public Field byId(String id) throws DotDataException {

    return selectInDb(id);
  }

  @Override
  public Field byContentTypeFieldVar(ContentType type, String var) throws DotDataException {
    return byContentTypeIdFieldVar(type.inode(), var);
  }

  @Override
  public Field byContentTypeIdFieldVar(String id, String var) throws DotDataException {
    return selectByContentTypeFieldVarInDb(id, var);
  }

  @Override
  public List<Field> byContentType(ContentType type) throws DotDataException {
    return type.fields();
  }

  @Override
  public List<Field> byContentTypeId(String id) throws DotDataException {
    return selectByContentTypeInDb(id);
  }

  @Override
  public List<Field> byContentTypeVar(String var) throws DotDataException {
    return selectByContentTypeVarInDb(var);
  }

  @Override
  public void delete(Field field) throws DotDataException {
    LocalTransaction.wrapReturn(() -> {
      return deleteFieldInDb(field);
    });
  }

  @Override
  public List<FieldVariable> loadVariables(Field field) throws DotDataException {
    return selectFieldVarsInDb(field);
  }



  @Override
  public Field save(final Field throwAwayField) throws DotDataException {
    Field f =  LocalTransaction.wrapReturn(() -> {
        return dbSaveUpdate(throwAwayField);
    });
    ContentType t= CacheLocator.getContentTypeCache2().byInode(f.contentTypeId());
    if(t!=null)CacheLocator.getContentTypeCache2().remove(t);
    return f;
  }


  private Field dbSaveUpdate(final Field throwAwayField) throws DotDataException {

    if (!throwAwayField.acceptedDataTypes().contains(throwAwayField.dataType())) {
      throw new DotDataValidationException("Field Type:" + throwAwayField.type()
          + " does not accept datatype " + throwAwayField.dataType(), "field.validation.incorrect.datatype");
    }
    if (throwAwayField.contentTypeId() == null) {
      throw new DotDataValidationException("Field Type:" + throwAwayField.type()
          + " does not have a contenttype.inode set", "field.validation.contenttype.not.set");
    }

    List<Field> fieldsAlreadyAdded = byContentTypeId(throwAwayField.contentTypeId());
    
    Date modDate = DateUtils.round(new Date(), Calendar.SECOND);
    FieldBuilder builder = FieldBuilder.builder(throwAwayField).modDate(modDate);

    

    Field oldField = null;
    try {
      oldField = selectInDb(throwAwayField.inode());
    } catch (NotFoundInDbException e) {
      // this is a new field
    }



    if (oldField == null) {
      // assign a db column if we need to
      if (throwAwayField.dbColumn() == null) {
        builder.dbColumn(assignAvailableColumn(throwAwayField));
      }
      // assign an inode if needed
      if (throwAwayField.inode() == null) {
        builder.inode(UUID.randomUUID().toString());
      }
      int x  =throwAwayField.sortOrder();
      if(throwAwayField.sortOrder()<0){
        //move to the end of the line
        builder.sortOrder((int) ((System.currentTimeMillis()* ((fieldsAlreadyAdded.size()+1))) / 1000L));
      }
      //normalize our velocityvar
      builder.variable(suggestVelocityVar(throwAwayField.name(), fieldsAlreadyAdded));
    }
    
    




    for (Field f : fieldsAlreadyAdded) {
      if (f instanceof CategoryField) {
        if (f.values() != null) {
          if (f.values().equals(throwAwayField.values())) {
            if (f.inode().equals(throwAwayField.inode())) {
              throw new DotDataValidationException(
                  "This category field already exists on this content type",
                  "message.category.existing.field");
            }
          }
        }
      }
      if (throwAwayField instanceof OnePerContentType) {
        if (f.inode().equals(throwAwayField.inode())) {
          continue;
        }
        if (f.type().equals(throwAwayField.type())) {
          throw new DotDataValidationException("A content type cannot have two:" + throwAwayField.type()
              + " fields","contenttype.validation.cannot.have.two.of.fieldtype");
        }
      }
    }

    // make sure we are properly indexed
    if ((throwAwayField.searchable() 
        || throwAwayField.listed()) 
        || throwAwayField.unique()
        || throwAwayField instanceof HostFolderField 
        || throwAwayField instanceof TagField) {
      builder.indexed(true);
    }
    if (throwAwayField.unique()) {
      builder.required(true);
    }



    Field retField = builder.build();

    if (oldField==null) {
      insertInodeInDb(retField);
      insertFieldInDb(retField);
    } else {
      updateInodeInDb(retField);
      updateFieldInDb(retField);
    }



    return retField;
  }

  @Override
  public List<Field> selectByContentTypeInDb(String id) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.findByContentType);
    dc.addParam(id);
    List<Map<String, Object>> results;
    results = dc.loadObjectResults();
    return new DbFieldTransformer(results).asList();

  }

  private Field selectByContentTypeFieldVarInDb(String id, String var) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.findByContentTypeAndFieldVar).addParam(id).addParam(var);


    List<Map<String, Object>> results;

    results = dc.loadObjectResults();
    if (results.size() == 0) {
      throw new NotFoundInDbException("Field with contentype:" + id + " and var:" + var
          + " not found");
    }
    return new DbFieldTransformer(results.get(0)).from();

  }

  private List<Field> selectByContentTypeVarInDb(String var) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.findByContentTypeVar);
    dc.addParam(var);
    List<Map<String, Object>> results;
    results = dc.loadObjectResults();
    return new DbFieldTransformer(results).asList();

  }

  private Field selectInDb(String id) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.findById);
    dc.addParam(id);
    List<Map<String, Object>> results;

    results = dc.loadObjectResults();
    if (results.size() == 0) {
      throw new NotFoundInDbException("Field with id:" + id + " not found");
    }
    return new DbFieldTransformer(results.get(0)).from();
  }

  private boolean deleteFieldInDb(Field field) throws DotDataException {
    deleteFieldVarsInDb(field);
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.deleteById);
    dc.addParam(field.inode());
    dc.loadResult();
    dc.setSQL(sql.deleteInodeById);
    dc.addParam(field.inode());
    dc.loadResult();
    return true;
  }

  private void updateInodeInDb(Field field) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.updateFieldInode);
    dc.addParam(field.inode());
    dc.addParam(field.iDate());
    dc.addParam(field.owner());
    dc.addParam(field.inode());
    dc.loadResult();
  }

  private void insertInodeInDb(Field field) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.insertFieldInode);
    dc.addParam(field.inode());
    dc.addParam(field.iDate());
    dc.addParam(field.owner());
    dc.loadResult();
  }

  private List<FieldVariable> selectFieldVarsInDb(Field field) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.selectFieldVars);
    dc.addParam(field.inode());
    return new DbFieldVariableTransformer(dc.loadObjectResults()).asList();
  }

  private FieldVariable selectFieldVarInDb(String id) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.selectFieldVar);
    dc.addParam(id);
    return new DbFieldVariableTransformer(dc.loadObjectResults()).from();
  }

  private FieldVariable upsertFieldVariable(FieldVariable var) throws DotDataException {

    var =
        ImmutableFieldVariable.builder().from(var)
            .modDate(DateUtils.round(new Date(), Calendar.SECOND)).build();
    DotConnect dc = new DotConnect();
    // delete first
    if (var.id() != null) {
      deleteFieldVarInDb(var);
    } else {
      var = ImmutableFieldVariable.builder().from(var).id(UUID.randomUUID().toString()).build();
    }

    dc.setSQL(sql.insertFieldVar);
    dc.addParam(var.id());
    dc.addParam(var.fieldId());
    dc.addParam(var.name());
    dc.addParam(var.key());
    dc.addParam(var.value());
    dc.addParam(var.userId());
    dc.addParam(var.modDate());
    dc.loadResult();
    return var;
  }

  private void deleteFieldVarInDb(FieldVariable var) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.deleteFieldVar);
    dc.addParam(var.id());
    dc.loadResult();
  }

  private void deleteFieldVarsInDb(Field field) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.deleteFieldVarsForField);
    dc.addParam(field.inode());
    dc.loadResult();
  }

  private void updateFieldInDb(Field field) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.updateField);
    dc.addParam(field.contentTypeId());
    dc.addParam(field.name());
    dc.addParam(field.type().getCanonicalName());
    dc.addParam(field.relationType());
    dc.addParam(field.required());
    dc.addParam(field.indexed());
    dc.addParam(field.listed());
    dc.addParam(field.variable());
    dc.addParam(field.sortOrder());
    dc.addParam(field.values());
    dc.addParam(field.regexCheck());
    dc.addParam(field.hint());
    dc.addParam(field.defaultValue());
    dc.addParam(field.fixed());
    dc.addParam(field.readOnly());
    dc.addParam(field.searchable());
    dc.addParam(field.unique());
    dc.addParam(field.modDate());
    dc.addParam(field.inode());
    dc.loadResult();

  }

  private void insertFieldInDb(Field field) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.insertField);
    dc.addParam(field.inode());
    dc.addParam(field.contentTypeId());
    dc.addParam(field.name());
    dc.addParam(field.type().getCanonicalName());
    dc.addParam(field.relationType());
    dc.addParam(field.dbColumn());
    dc.addParam(field.required());
    dc.addParam(field.indexed());
    dc.addParam(field.listed());
    dc.addParam(field.variable());
    dc.addParam(field.sortOrder());
    dc.addParam(field.values());
    dc.addParam(field.regexCheck());
    dc.addParam(field.hint());
    dc.addParam(field.defaultValue());
    dc.addParam(field.fixed());
    dc.addParam(field.readOnly());
    dc.addParam(field.searchable());
    dc.addParam(field.unique());
    dc.addParam(field.modDate());

    dc.loadResult();

  }

  private int inodeCount(Field field) throws DotDataException {
    DotConnect dc = new DotConnect();
    dc.setSQL(sql.inodeCount);
    dc.addParam(field.inode());
    return dc.getInt("inode_count");

  }

  private String assignAvailableColumn(Field field) throws DotDataException {



    DotConnect dc = new DotConnect();

    if (field instanceof HostFolderField || field instanceof TagField) {
      dc.setSQL(this.sql.selectCountOfType);
      dc.addParam(field.contentTypeId());
      dc.addParam(LegacyFieldTypes.getLegacyName(field.type() + "%"));
      dc.addParam(LegacyFieldTypes.getImplClass(field.type() + "%"));
      int x = dc.getInt("test");
      if (x > 0) {
        throw new OverFieldLimitException("Only one " + field.type() + " per ContentType");
      }
    }

    if (field.dataType() == DataTypes.CONSTANT || field.dataType() == DataTypes.SECTION_DIVIDER
        || field.dataType() == DataTypes.SYSTEM) {
      return field.dataType().toString();
    }


    String dataType = field.dataType().toString();
    dc.setSQL(this.sql.selectFieldOfDbType);
    dc.addParam(field.contentTypeId());
    dc.addParam(dataType + "%");
    List<Map<String, Object>> rows = dc.loadObjectResults();
    Set<String> columns = new TreeSet<String>();
    for (int i = 0; i < rows.size(); i++) {
      columns.add((String) rows.get(i).get("field_contentlet"));
    }

    for (int i = 0; i < Config.getIntProperty("db.number.of.contentlet.columns.per.datatype", 25); i++) {
      if (!columns.contains(dataType + (i + 1))) {
        return dataType + (i + 1);

      }
    }

    throw new OverFieldLimitException("No more columns for datatype:" + dataType);


  }

  @Override
  public void deleteByContentType(ContentType type) throws DotDataException {
    List<Field> fields = byContentType(type);
    for (Field field : fields) {
      deleteFieldInDb(field);
    }
  }
  
  
  @Override
  public String suggestVelocityVar(final String tryVar, List<Field> takenFields) throws DotDataException{

      String var = VelocityUtil.convertToVelocityVariable(tryVar);

      for(Field f : takenFields){
        if(var.equalsIgnoreCase(f.variable())){
          var=null;
          break;
        }
      }
      if(var!=null) return var;
      
      for(int i=1;i<100000;i++){
        var = VelocityUtil.convertToVelocityVariable(tryVar) + i;
        for(Field f : takenFields){
          if(var.equals(f.variable())){
            var=null;
            break;
          }
        }
        if(var!=null) return var;
      }
      throw new DotDataValidationException("Unable to suggest a variable name.  Got to:"+ var, "field.validation.variable.already.taken");
      
  }
}
