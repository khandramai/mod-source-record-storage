package org.folio.dao;

import io.github.jklingsporn.vertx.jooq.classic.reactivepg.ReactiveClassicGenericQueryExecutor;
import io.reactivex.Flowable;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.sf.jsqlparser.JSQLParserException;
import org.folio.dao.util.CompositeMatchField;
import org.folio.dao.util.IdType;
import org.folio.dao.util.MatchField;
import org.folio.dao.util.RecordType;
import org.folio.rest.jaxrs.model.Filter;
import org.folio.rest.jaxrs.model.MarcBibCollection;
import org.folio.rest.jaxrs.model.ParsedRecord;
import org.folio.rest.jaxrs.model.ParsedRecordsBatchResponse;
import org.folio.rest.jaxrs.model.Record;
import org.folio.rest.jaxrs.model.RecordCollection;
import org.folio.rest.jaxrs.model.RecordsBatchResponse;
import org.folio.rest.jaxrs.model.RecordsIdentifiersCollection;
import org.folio.rest.jaxrs.model.SourceRecord;
import org.folio.rest.jaxrs.model.SourceRecordCollection;
import org.folio.rest.jaxrs.model.StrippedParsedRecordCollection;
import org.folio.rest.jooq.enums.RecordState;
import org.folio.services.RecordSearchParameters;
import org.folio.services.entities.RecordsModifierOperator;
import org.folio.services.util.TypeConnection;
import org.folio.services.util.parser.ParseFieldsResult;
import org.folio.services.util.parser.ParseLeaderResult;
import org.jooq.Condition;
import org.jooq.OrderField;

/**
 * Data access object for {@link Record}
 */
public interface RecordDao {

  /**
   * Searches for {@link Record} by {@link Condition} and ordered by collection of {@link OrderField} with offset and limit
   *
   * @param condition   query where condition
   * @param recordType  record type
   * @param orderFields fields to order by
   * @param offset      starting index in a list of results
   * @param limit       limit of records for pagination
   * @param tenantId    tenant id
   * @return {@link Future} of {@link RecordCollection}
   */
  Future<RecordCollection> getRecords(Condition condition, RecordType recordType, Collection<OrderField<?>> orderFields, int offset, int limit, String tenantId);

  /**
   * Searches for {@link Record} by {@link Condition} and ordered by collection of {@link OrderField} with offset and limit
   *
   * @param condition        query where condition
   * @param recordType       record type
   * @param orderFields      fields to order by
   * @param offset           starting index in a list of results
   * @param limit            limit of records for pagination
   * @param returnTotalCount indicates that total records count should/shouldn't be calculated and returned within {@link RecordCollection#totalRecords}
   * @param tenantId         tenant id
   * @return {@link Future} of {@link RecordCollection}
   */
  Future<RecordCollection> getRecords(Condition condition, RecordType recordType, Collection<OrderField<?>> orderFields, int offset, int limit, boolean returnTotalCount, String tenantId);

  /**
   * Searches for records by {@link Condition} with only {@link ParsedRecord} content
   *
   * @param externalIds list of ids
   * @param idType      external id type on which source record will be searched
   * @param recordType  record type
   * @param includeDeleted  searching by deleted records
   * @param tenantId    tenant id
   * @return {@link Future} of {@link StrippedParsedRecordCollection}
   */
  Future<StrippedParsedRecordCollection> getStrippedParsedRecords(List<String> externalIds, IdType idType, RecordType recordType, Boolean includeDeleted, String tenantId);

  /**
   * Searches for {@link Record} by {@link MatchField} with offset and limit
   *
   * @param matchField          Marc field that needs to be matched
   * @param comparisonPartType  describes type of comparison part
   * @param matchedRecordIds    list of records IDs that will be used as additional criteria for filtering records
   *                            that match the specified {@code MatchField} criteria
   * @param recordType          record type
   * @param externalIdRequired  specifies whether necessary not to consider records with {@code externalId == null} while searching
   * @param offset              starting index in a list of results
   * @param limit               limit of records for pagination
   * @param tenantId            tenant id
   * @return  {@link Future} of {@link RecordCollection}
   */
  Future<List<Record>> getMatchedRecords(MatchField matchField, Filter.ComparisonPartType comparisonPartType,
                                         List<String> matchedRecordIds, TypeConnection recordType, boolean externalIdRequired,
                                         int offset, int limit, String tenantId);

  /**
   * Searches for {@link Record} by {@link MatchField} with offset and limit,
   * and returns {@link RecordsIdentifiersCollection} representing list of pairs of recordId and externalId
   *
   * @param matchedField        describes searching condition
   * @param returnTotalRecords  indicates that amount of total records should/shouldn't be calculated
   *                            and populated into {@link RecordsIdentifiersCollection#totalRecords}
   * @param typeConnection      record type
   * @param externalIdRequired  specifies whether necessary not to consider records with {@code externalId == null} while searching
   * @param offset              offset to skip over a number of elements
   * @param limit               limit of records
   * @param tenantId            tenant id
   * @return {@link Future} of {@link RecordsIdentifiersCollection}
   */
  Future<RecordsIdentifiersCollection> getMatchedRecordsIdentifiers(CompositeMatchField matchedField, boolean returnTotalRecords,
                                                                    TypeConnection typeConnection, boolean externalIdRequired,
                                                                    int offset, int limit, String tenantId);

  /**
   * Streams {@link Record} by {@link Condition} and ordered by collection of {@link OrderField}
   *
   * @param condition   query where condition
   * @param recordType  record type
   * @param orderFields fields to order by
   * @param offset      starting index in a list of results
   * @param limit       limit of records
   * @param tenantId    tenant id
   * @return {@link Flowable} of {@link Record}
   */
  Flowable<Record> streamRecords(Condition condition, RecordType recordType, Collection<OrderField<?>> orderFields, int offset, int limit, String tenantId);


  /**
   * Stream [instanceId, totalCount] of the marc record by search expressions with offset and limit
   *
   * @param parseLeaderResult     result of parsing leaderSearchExpression
   * @param parseFieldsResult     result of parsing fieldsSearchExpression
   * @param searchParameters      additional parameters needed for search
   * @param tenantId              tenant id
   * @return {@link Flowable} of {@link Record id}
   */
  Flowable<Row> streamMarcRecordIds(ParseLeaderResult parseLeaderResult, ParseFieldsResult parseFieldsResult, RecordSearchParameters searchParameters, String tenantId) throws JSQLParserException;

  /**
   * Searches for {@link Record} by id
   *
   * @param id       record id
   * @param tenantId tenant id
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordById(String id, String tenantId);

  /**
   * Searches for {@link Record} by id using {@link ReactiveClassicGenericQueryExecutor}
   *
   * @param txQE query execution
   * @param id   Record id
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordById(ReactiveClassicGenericQueryExecutor txQE, String id);

  /**
   * Searches for {@link Record} by matchedId
   *
   * @param matchedId Record matchedId
   * @param tenantId  tenant id
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordByMatchedId(String matchedId, String tenantId);

  /**
   * Searches for {@link Record} by matchedId using {@link ReactiveClassicGenericQueryExecutor}
   *
   * @param txQE      query execution
   * @param matchedId Record matchedId
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordByMatchedId(ReactiveClassicGenericQueryExecutor txQE, String matchedId);

  /**
   * Searches for {@link Record} by condition
   *
   * @param condition query where condition
   * @param tenantId  tenant id
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordByCondition(Condition condition, String tenantId);

  /**
   * Searches for {@link Record} by {@link Condition} using {@link ReactiveClassicGenericQueryExecutor}
   *
   * @param txQE      query executor
   * @param condition query where condition
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordByCondition(ReactiveClassicGenericQueryExecutor txQE, Condition condition);

  /**
   * Saves {@link Record} to the db
   *
   * @param record   Record to save
   * @param okapiHeaders okapi headers
   * @return future with saved Record
   */
  Future<Record> saveRecord(Record record, Map<String, String> okapiHeaders);

  /**
   * Saves {@link Record} to the db using {@link ReactiveClassicGenericQueryExecutor}
   *
   * @param txQE   query executor
   * @param record Record to save
   * @return future with saved Record
   */
  Future<Record> saveRecord(ReactiveClassicGenericQueryExecutor txQE, Record record, Map<String, String> okapiHeaders);

  /**
   * Saves {@link RecordCollection} to the db.
   *
   * @param recordCollection Record collection to save
   * @param okapiHeaders okapi headers
   * @return future with saved {@link RecordsBatchResponse}
   */
  Future<RecordsBatchResponse> saveRecords(RecordCollection recordCollection, Map<String, String> okapiHeaders);

  /**
   * Saves {@link RecordCollection} to the db.
   *
   * @param externalIds external relation ids
   * @param recordType  record type
   * @param recordsModifier records collection modifier operator
   * @param okapiHeaders okapi headers
   * @return future with saved {@link RecordsBatchResponse}
   */
  Future<RecordsBatchResponse> saveRecordsByExternalIds(List<String> externalIds,
                                                        RecordType recordType,
                                                        RecordsModifierOperator recordsModifier,
                                                        Map<String, String> okapiHeaders);

  /**
   * Updates {{@link Record} in the db
   *
   * @param record   Record to update
   * @param okapiHeaders okapi headers
   * @return future with updated Record
   */
  Future<Record> updateRecord(Record record, Map<String, String> okapiHeaders);

  /**
   * Increments generation in case a record with the same matchedId exists
   * and the snapshot it is linked to is COMMITTED before the processing of the current one started
   *
   * @param txQE   query execution
   * @param record Record
   * @return future with generation
   */
  Future<Integer> calculateGeneration(ReactiveClassicGenericQueryExecutor txQE, Record record);

  /**
   * Updates {@link ParsedRecord} in the db
   *
   * @param record   record dto from which {@link ParsedRecord} will be updated
   * @param okapiHeaders okapi headers
   * @return future with updated ParsedRecord
   */
  Future<ParsedRecord> updateParsedRecord(Record record, Map<String, String> okapiHeaders);

  /**
   * Update parsed records from collection of records and external relations ids in one transaction
   *
   * @param recordCollection collection of records from which parsed records will be updated
   * @param okapiHeaders okapi headers
   * @return future with response containing list of successfully updated records and error messages for records that were not updated
   */
  Future<ParsedRecordsBatchResponse> updateParsedRecords(RecordCollection recordCollection, Map<String, String> okapiHeaders);

  /**
   * Searches for {@link Record} by id of external entity which was created from desired record
   *
   * @param externalId     external relation id
   * @param idType external id type
   * @param tenantId       tenant id
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordByExternalId(String externalId, IdType idType, String tenantId);

  /**
   * Searches for {@link Record} by id of external entity which was created from desired record
   *
   * @param txQE           query execution
   * @param externalId     external relation id
   * @param idType external id type
   * @return future with optional {@link Record}
   */
  Future<Optional<Record>> getRecordByExternalId(ReactiveClassicGenericQueryExecutor txQE, String externalId, IdType idType);

  /**
   * Searches for {@link SourceRecord} by {@link Condition} and ordered by order fields with offset and limit
   *
   * @param condition   query where condition
   * @param recordType  record type
   * @param orderFields fields to order by
   * @param offset      starting index in a list of results
   * @param limit       limit of records for pagination
   * @param tenantId    tenant id
   * @return future with {@link SourceRecordCollection}
   */
  Future<SourceRecordCollection> getSourceRecords(Condition condition, RecordType recordType, Collection<OrderField<?>> orderFields, int offset, int limit, String tenantId);

  /**
   * Stream {@link SourceRecord} by {@link Condition} and ordered by order fields with offset and limit
   *
   * @param condition   query where condition
   * @param recordType  record type
   * @param orderFields fields to order by
   * @param offset      starting index in a list of results
   * @param limit       limit of records for pagination
   * @param tenantId    tenant id
   * @return {@link Flowable} of {@link SourceRecord}
   */
  Flowable<SourceRecord> streamSourceRecords(Condition condition, RecordType recordType, Collection<OrderField<?>> orderFields, int offset, int limit, String tenantId);

  /**
   * Searches for {@link SourceRecord} where id in a list of ids defined by external id type. i.e. INSTANCE or RECORD
   *
   * @param ids            list of ids
   * @param idType external id type on which source record will be searched
   * @param recordType     record type
   * @param deleted        filter by state DELETED or leader record status d, s, or x
   * @param tenantId       tenant id
   * @return future with {@link SourceRecordCollection}
   */
  Future<SourceRecordCollection> getSourceRecords(List<String> ids, IdType idType, RecordType recordType, Boolean deleted, String tenantId);

  /**
   * Searches for {@link SourceRecord} by {@link Condition}
   *
   * @param condition query where condition
   * @param tenantId  tenant id
   * @return return future with optional {@link SourceRecord}
   */
  Future<Optional<SourceRecord>> getSourceRecordByCondition(Condition condition, String tenantId);

  /**
   * Searches for {@link SourceRecord} by external entity which was created from desired record by specific type.
   *
   * @param id             id
   * @param idType external id type on which source record will be searched
   * @param state external record state on which source record will be searched
   * @param tenantId       tenant id
   * @return return future with optional {@link SourceRecord}
   */
  Future<Optional<SourceRecord>> getSourceRecordByExternalId(String id, IdType idType, RecordState state, String tenantId);

  /**
   * Deletes in transaction all records associated with specified snapshot and snapshot itself
   *
   * @param snapshotId snapshot id
   * @param tenantId   tenant id
   * @return future with true if succeeded
   */
  Future<Boolean> deleteRecordsBySnapshotId(String snapshotId, String tenantId);

  /**
   * Deletes in transaction all records associated with externalId
   *
   * @param externalId  external id
   * @param okapiHeaders okapi headers
   * @return future with true if succeeded
   */
  Future<Boolean> deleteRecordsByExternalId(String externalId, Map<String, String> okapiHeaders);

  /**
   *  Performs purge the 'DELETED' records.
   *  Purges a given limited number of 'DELETED' records updated more the than given number of days back.
   *
   * @param lastUpdatedDays number of days when the records were updated the last time
   * @param limit           maximum number of records allowed to purge
   * @param tenantId        tenant id
   * @return void future
   */
  Future<Void> deleteRecords(int lastUpdatedDays, int limit, String tenantId);

  /**
   * Performs purge of marc indexer versions that are not the latest for each marc record
   *
   * @return boolean future
   */
  Future<Boolean> deleteMarcIndexersOldVersions(String tenantId, Integer oneTimeLimit);

  /**
   * Creates new Record and updates status of the "old" one,
   * no data is overwritten as a result of update. Creates
   * new snapshot.
   *
   * @param txQE      query execution
   * @param newRecord new Record to create
   * @param oldRecord old Record that has to be marked as "old"
   * @param okapiHeaders okapi headers
   * @return future with new "updated" Record
   */
  Future<Record> saveUpdatedRecord(ReactiveClassicGenericQueryExecutor txQE, Record newRecord, Record oldRecord, Map<String, String> okapiHeaders);

  /**
   * Change suppress from discovery flag for record by external relation id
   *
   * @param id             id
   * @param idType external id type
   * @param suppress       suppress from discovery
   * @param tenantId       tenant id
   * @return future with true if succeeded
   */
  Future<Boolean> updateSuppressFromDiscoveryForRecord(String id, IdType idType, Boolean suppress, String tenantId);

  /**
   * Execute action within transaction.
   *
   * @param <T>      future generic type
   * @param action   action
   * @param tenantId tenant id
   * @return future with generic type
   */
  <T> Future<T> executeInTransaction(Function<ReactiveClassicGenericQueryExecutor, Future<T>> action, String tenantId);

  /**
   * Search for non-existent mark bib ids in the system
   *
   * @param marcBibIds list of marc bib ids
   * @param tenantId tenant id
   * @return future with list of invalid marc bib ids
   */
  Future<MarcBibCollection> verifyMarcBibRecords(List<String> marcBibIds, String tenantId);

  /**
   * Updates a multiple records that have same matched id. It's usually the actual record,
   * and related records of an older generations.
   *
   * @param matchedId matched id
   * @param recordState record state
   * @param recordType record type
   * @param tenantId  tenant id
   * @return void future
   */
  Future<Void> updateRecordsState(String matchedId, RecordState recordState, RecordType recordType, String tenantId);
}
