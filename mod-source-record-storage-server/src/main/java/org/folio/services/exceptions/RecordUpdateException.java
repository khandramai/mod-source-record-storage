package org.folio.services.exceptions;

/**
 * Exception to indicate a record update error
 */
public class RecordUpdateException extends RuntimeException {

  public RecordUpdateException(String message) {
    super(message);
  }

  public RecordUpdateException(Throwable cause) {
    super(cause);
  }
}
