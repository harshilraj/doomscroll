package com.antidoomscroll.app.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ActionLogDao_Impl implements ActionLogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ActionLog> __insertionAdapterOfActionLog;

  public ActionLogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfActionLog = new EntityInsertionAdapter<ActionLog>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `action_logs` (`id`,`actionText`,`userInput`,`timestamp`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ActionLog entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getActionText());
        if (entity.getUserInput() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getUserInput());
        }
        statement.bindLong(4, entity.getTimestamp());
      }
    };
  }

  @Override
  public Object insert(final ActionLog actionLog, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfActionLog.insert(actionLog);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCountToday(final long startOfDay,
      final Continuation<? super Integer> $completion) {
    final String _sql = "\n"
            + "        SELECT COUNT(*) FROM action_logs \n"
            + "        WHERE timestamp >= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startOfDay);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getRecentActionTexts(final int limit,
      final Continuation<? super List<String>> $completion) {
    final String _sql = "\n"
            + "        SELECT actionText FROM action_logs \n"
            + "        ORDER BY timestamp DESC \n"
            + "        LIMIT ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTodayActions(final long startOfDay,
      final Continuation<? super List<ActionLog>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM action_logs \n"
            + "        WHERE timestamp >= ? \n"
            + "        ORDER BY timestamp DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startOfDay);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ActionLog>>() {
      @Override
      @NonNull
      public List<ActionLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActionText = CursorUtil.getColumnIndexOrThrow(_cursor, "actionText");
          final int _cursorIndexOfUserInput = CursorUtil.getColumnIndexOrThrow(_cursor, "userInput");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<ActionLog> _result = new ArrayList<ActionLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpActionText;
            _tmpActionText = _cursor.getString(_cursorIndexOfActionText);
            final String _tmpUserInput;
            if (_cursor.isNull(_cursorIndexOfUserInput)) {
              _tmpUserInput = null;
            } else {
              _tmpUserInput = _cursor.getString(_cursorIndexOfUserInput);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new ActionLog(_tmpId,_tmpActionText,_tmpUserInput,_tmpTimestamp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllActions(final Continuation<? super List<ActionLog>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM action_logs \n"
            + "        ORDER BY timestamp DESC\n"
            + "        LIMIT 100\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ActionLog>>() {
      @Override
      @NonNull
      public List<ActionLog> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActionText = CursorUtil.getColumnIndexOrThrow(_cursor, "actionText");
          final int _cursorIndexOfUserInput = CursorUtil.getColumnIndexOrThrow(_cursor, "userInput");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<ActionLog> _result = new ArrayList<ActionLog>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActionLog _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpActionText;
            _tmpActionText = _cursor.getString(_cursorIndexOfActionText);
            final String _tmpUserInput;
            if (_cursor.isNull(_cursorIndexOfUserInput)) {
              _tmpUserInput = null;
            } else {
              _tmpUserInput = _cursor.getString(_cursorIndexOfUserInput);
            }
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new ActionLog(_tmpId,_tmpActionText,_tmpUserInput,_tmpTimestamp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
