package org.folio.services.util;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.dao.util.ParsedRecordDaoUtil;
import org.folio.processing.mapping.defaultmapper.processor.parameters.MappingParameters;
import org.folio.rest.jaxrs.model.MarcFieldProtectionSetting;
import org.folio.rest.jaxrs.model.Record;
import org.folio.services.exceptions.PostProcessingException;
import org.marc4j.MarcJsonReader;
import org.marc4j.MarcJsonWriter;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamWriter;
import org.marc4j.MarcWriter;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Util to work with additional fields
 */
public final class AdditionalFieldsUtil {

  public static final String TAG_005 = "005";
  public static final String TAG_999 = "999";

  public static final String HR_ID_FROM_FIELD = "001";
  private static final String HR_ID_PREFIX_FROM_FIELD = "003";
  private static final String HR_ID_TO_FIELD = "035";
  private static final String HR_ID_FIELD = "hrid";
  private static final char HR_ID_FIELD_SUB = 'a';
  private static final char HR_ID_FIELD_IND = ' ';
  private static final String ANY_STRING = "*";

  public static final DateTimeFormatter dateTime005Formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.S");

  private static final Logger LOGGER = LogManager.getLogger();
  private static final char INDICATOR = 'f';

  private AdditionalFieldsUtil() {
  }

  /**
   * Adds field if it does not exist and a subfield with a value to that field
   *
   * @param record   record that needs to be updated
   * @param field    field that should contain new subfield
   * @param subfield new subfield to add
   * @param value    value of the subfield to add
   * @return true if succeeded, false otherwise
   */
  public static boolean addFieldToMarcRecord(Record record, String field, char subfield, String value) {
    boolean result = false;
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      if (record != null && record.getParsedRecord() != null && record.getParsedRecord().getContent() != null) {
        MarcReader reader = buildMarcReader(record);
        MarcWriter streamWriter = new MarcStreamWriter(new ByteArrayOutputStream());
        MarcJsonWriter jsonWriter = new MarcJsonWriter(os);
        MarcFactory factory = MarcFactory.newInstance();
        if (reader.hasNext()) {
          org.marc4j.marc.Record marcRecord = reader.next();
          VariableField variableField = getSingleFieldByIndicators(marcRecord.getVariableFields(field), INDICATOR, INDICATOR);
          DataField dataField;
          if (variableField != null
            && ((DataField) variableField).getIndicator1() == INDICATOR
            && ((DataField) variableField).getIndicator2() == INDICATOR
          ) {
            dataField = (DataField) variableField;
            marcRecord.removeVariableField(variableField);
            dataField.removeSubfield(dataField.getSubfield(subfield));
          } else {
            dataField = factory.newDataField(field, INDICATOR, INDICATOR);
          }
          dataField.addSubfield(factory.newSubfield(subfield, value));
          marcRecord.addVariableField(dataField);
          // use stream writer to recalculate leader
          streamWriter.write(marcRecord);
          jsonWriter.write(marcRecord);
          record.setParsedRecord(record.getParsedRecord().withContent(new JsonObject(os.toString()).encode()));
          result = true;
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to add additional subfield {} for field {} to record {}", subfield, field, record.getId(), e);
    }
    return result;
  }

  /**
   * Adds new controlled field to marc record
   *
   * @param record record that needs to be updated
   * @param field  tag of controlled field
   * @param value  value of the field to add
   * @return true if succeeded, false otherwise
   */
  public static boolean addControlledFieldToMarcRecord(Record record, String field, String value) {
    return addControlledFieldToMarcRecord(record, field, value, false);
  }

  public static boolean addControlledFieldToMarcRecord(Record record, String field, String value, boolean replace) {
    boolean result = false;
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      if (record != null && record.getParsedRecord() != null && record.getParsedRecord().getContent() != null) {
        if (replace) {
          removeField(record, field);
        }
        MarcReader reader = buildMarcReader(record);
        MarcWriter streamWriter = new MarcStreamWriter(new ByteArrayOutputStream());
        MarcJsonWriter jsonWriter = new MarcJsonWriter(os);

        MarcFactory factory = MarcFactory.newInstance();
        if (reader.hasNext()) {
          org.marc4j.marc.Record marcRecord = reader.next();
          ControlField dataField = factory.newControlField(field, value);
          marcRecord.addVariableField(dataField);
          // use stream writer to recalculate leader
          streamWriter.write(marcRecord);
          jsonWriter.write(marcRecord);
          record.setParsedRecord(record.getParsedRecord().withContent(new JsonObject(os.toString()).encode()));
          result = true;
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to add additional controlled field {) to record {}", field, record.getId(), e);
    }
    return result;
  }

  /**
   * remove field from marc record
   *
   * @param record record that needs to be updated
   * @param field  tag of the field
   * @return true if succeeded, false otherwise
   */
  public static boolean removeField(Record record, String field) {
    boolean result = false;
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      if (record != null && record.getParsedRecord() != null && record.getParsedRecord().getContent() != null) {
        MarcReader reader = buildMarcReader(record);
        MarcWriter marcStreamWriter = new MarcStreamWriter(new ByteArrayOutputStream());
        MarcJsonWriter marcJsonWriter = new MarcJsonWriter(baos);
        if (reader.hasNext()) {
          org.marc4j.marc.Record marcRecord = reader.next();
          VariableField variableField = marcRecord.getVariableField(field);
          if (variableField != null) {
            marcRecord.removeVariableField(variableField);
          }
          // use stream writer to recalculate leader
          marcStreamWriter.write(marcRecord);
          marcJsonWriter.write(marcRecord);
          record.setParsedRecord(record.getParsedRecord().withContent(new JsonObject(baos.toString()).encode()));
          result = true;
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to remove controlled field {) from record {}", field, record.getId(), e);
    }
    return result;
  }

  /**
   * Read value from controlled field in marc record
   *
   * @param record marc record
   * @param tag    tag to read
   * @return value from field
   */
  public static String getValueFromControlledField(Record record, String tag) {
    try {
      MarcReader reader = buildMarcReader(record);
      if (reader.hasNext()) {
        org.marc4j.marc.Record marcRecord = reader.next();
        Optional<ControlField> controlField = marcRecord.getControlFields()
          .stream()
          .filter(field -> field.getTag().equals(tag))
          .findFirst();
        if (controlField.isPresent()) {
          return controlField.get().getData();
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to read controlled field {) from record {}", tag, record.getId(), e);
      return null;
    }
    return null;
  }

  /**
   * Adds new data field to marc record
   *
   * @param record record that needs to be updated
   * @param tag    tag of data field
   * @param value  value of the field to add
   * @return true if succeeded, false otherwise
   */
  public static boolean addDataFieldToMarcRecord(Record record, String tag, char ind1, char ind2, char subfield, String value) {
    boolean result = false;
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      if (record != null && record.getParsedRecord() != null && record.getParsedRecord().getContent() != null) {
        MarcReader reader = buildMarcReader(record);
        MarcWriter streamWriter = new MarcStreamWriter(new ByteArrayOutputStream());
        MarcJsonWriter jsonWriter = new MarcJsonWriter(os);
        MarcFactory factory = MarcFactory.newInstance();
        if (reader.hasNext()) {
          org.marc4j.marc.Record marcRecord = reader.next();
          DataField dataField = factory.newDataField(tag, ind1, ind2);
          dataField.addSubfield(factory.newSubfield(subfield, value));
          addDataFieldInNumericalOrder(dataField, marcRecord);
          // use stream writer to recalculate leader
          streamWriter.write(marcRecord);
          jsonWriter.write(marcRecord);
          record.setParsedRecord(record.getParsedRecord().withContent(new JsonObject(os.toString()).encode()));
          result = true;
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed to add additional data field {) to record {}", e, tag, record.getId());
    }
    return result;
  }

  private static void addDataFieldInNumericalOrder(DataField field, org.marc4j.marc.Record marcRecord) {
    String tag = field.getTag();
    List<DataField> dataFields = marcRecord.getDataFields();
    for (int i = 0; i < dataFields.size(); i++) {
      if (dataFields.get(i).getTag().compareTo(tag) > 0) {
        marcRecord.getDataFields().add(i, field);
        return;
      }
    }
    marcRecord.addVariableField(field);
  }

  /**
   * Check if data field with the same value exist
   *
   * @param record record that needs to be updated
   * @param tag    tag of data field
   * @param value  value of the field to add
   * @return true if exist
   */
  public static boolean isFieldExist(Record record, String tag, char subfield, String value) {
    if (record != null && record.getParsedRecord() != null && record.getParsedRecord().getContent() != null) {
      MarcReader reader = buildMarcReader(record);
      try {
        if (reader.hasNext()) {
          org.marc4j.marc.Record marcRecord = reader.next();
          for (VariableField field : marcRecord.getVariableFields(tag)) {
            if (field instanceof DataField) {
              for (Subfield sub : ((DataField) field).getSubfields(subfield)) {
                if (isNotEmpty(sub.getData()) && sub.getData().equals(value.trim())) {
                  return true;
                }
              }
            } else if (field instanceof ControlField
              && isNotEmpty(((ControlField) field).getData())
              && ((ControlField) field).getData().equals(value.trim())) {
              return true;
            }
          }
        }
      } catch (Exception e) {
        LOGGER.error("Error during the search a field in the record", e);
        return false;
      }
    }
    return false;
  }

  /**
   * Move original marc hrId to 035 tag and assign created by inventory hrId into 001 tag
   *
   * @param recordInstancePair pair of related instance and record
   */
  public static void fillHrIdFieldInMarcRecord(Pair<Record, JsonObject> recordInstancePair) {
    String hrId = recordInstancePair.getValue().getString(HR_ID_FIELD);
    String valueFrom001 = getValueFromControlledField(recordInstancePair.getKey(), HR_ID_FROM_FIELD);
    String originalHrId = getValueFromControlledField(recordInstancePair.getKey(), HR_ID_FROM_FIELD);
    String originalHrIdPrefix = getValueFromControlledField(recordInstancePair.getKey(), HR_ID_PREFIX_FROM_FIELD);
    originalHrId = mergeFieldsFor035(originalHrIdPrefix, originalHrId);
    if (StringUtils.isNotEmpty(hrId) && StringUtils.isNotEmpty(originalHrId)) {
      removeField(recordInstancePair.getKey(), HR_ID_FROM_FIELD);
      removeField(recordInstancePair.getKey(), HR_ID_PREFIX_FROM_FIELD);
      addControlledFieldToMarcRecord(recordInstancePair.getKey(), HR_ID_FROM_FIELD, hrId);
      if (valueFrom001 != null && !isFieldExist(recordInstancePair.getKey(), HR_ID_TO_FIELD, HR_ID_FIELD_SUB, originalHrId)) {
        addDataFieldToMarcRecord(recordInstancePair.getKey(), HR_ID_TO_FIELD, HR_ID_FIELD_IND, HR_ID_FIELD_IND, HR_ID_FIELD_SUB, originalHrId);
      }
    } else if (StringUtils.isNotEmpty(hrId)) {
      addControlledFieldToMarcRecord(recordInstancePair.getKey(), HR_ID_FROM_FIELD, hrId);
    }
  }

  private static String mergeFieldsFor035(String valueFrom003, String valueFrom001) {
    if (isBlank(valueFrom003)) {
      return valueFrom001;
    }
    return "(" + valueFrom003 + ")" + valueFrom001;
  }

  private static MarcReader buildMarcReader(Record record) {
    String content = ParsedRecordDaoUtil.normalizeContent(record.getParsedRecord());
    return new MarcJsonReader(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  private static VariableField getSingleFieldByIndicators(List<VariableField> list, char ind1, char ind2) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.stream()
      .filter(f -> ((DataField) f).getIndicator1() == ind1 && ((DataField) f).getIndicator2() == ind2)
      .findFirst()
      .orElse(null);
  }

  /**
   * Updates field 005 for case when this field is not protected.
   *
   * @param record  record to update
   * @param context module context
   * @throws IOException
   */
  public static void updateLatestTransactionDate(Record record, HashMap<String, String> context) throws IOException {
    if (isField005NeedToUpdate(record, context)) {
      String date = AdditionalFieldsUtil.dateTime005Formatter.format(ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
      boolean isLatestTransactionDateUpdated = AdditionalFieldsUtil.addControlledFieldToMarcRecord(record, AdditionalFieldsUtil.TAG_005, date, true);
      if (!isLatestTransactionDateUpdated) {
        throw new PostProcessingException(format("Failed to update field '005' to record with id '%s'", record.getId()));
      }
    }
  }

  /**
   * Checks whether field 005 needs to be updated or this field is protected.
   *
   * @param record  record to check
   * @param context module context
   * @return true for case when field 005 have to updated
   * @throws IOException
   */
  private static boolean isField005NeedToUpdate(Record record, HashMap<String, String> context) throws IOException {
    boolean needToUpdate = true;
    List<MarcFieldProtectionSetting> fieldProtectionSettings = getFieldsProtectionSettings(context);
    if ((fieldProtectionSettings != null) && !fieldProtectionSettings.isEmpty()) {
      MarcReader reader = new MarcJsonReader(new ByteArrayInputStream(record.getParsedRecord().getContent().toString().getBytes()));
      if (reader.hasNext()) {
        org.marc4j.marc.Record marcRecord = reader.next();
        for (VariableField field : marcRecord.getVariableFields(AdditionalFieldsUtil.TAG_005)) {
          needToUpdate = isNotProtected(fieldProtectionSettings, (ControlField) field);
          break;
        }
      }
    }
    return needToUpdate;
  }

  /**
   * Prepares the list of MarcFieldProtectionSettings.
   *
   * @param context module context
   * @return List of MarcFieldProtectionSettings or empty list
   * @throws IOException
   */
  private static List<MarcFieldProtectionSetting> getFieldsProtectionSettings(HashMap<String, String> context) throws IOException {
    List<MarcFieldProtectionSetting> fieldProtectionSettings = new ArrayList<>();
    if (isNotBlank(context.get("MAPPING_PARAMS"))) {
      MappingParameters mappingParameters = (new ObjectMapper()).readValue(context.get("MAPPING_PARAMS"), MappingParameters.class);
      fieldProtectionSettings = mappingParameters.getMarcFieldProtectionSettings();
    }
    return fieldProtectionSettings;
  }

  /**
   * Checks is the control field is protected or not.
   *
   * @param fieldProtectionSettings List of MarcFieldProtectionSettings
   * @param field                   Control field that is being checked
   * @return true for case when control field isn't protected
   */
  private static boolean isNotProtected(List<MarcFieldProtectionSetting> fieldProtectionSettings, ControlField field) {
    return fieldProtectionSettings.stream()
      .filter(setting -> setting.getField().equals(ANY_STRING) || setting.getField().equals(field.getTag()))
      .noneMatch(setting -> setting.getData().equals(ANY_STRING) || setting.getData().equals(field.getData()));
  }
}
