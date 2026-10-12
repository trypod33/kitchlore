package com.trypod33.kitchlore.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.trypod33.kitchlore.data.local.entity.GlossaryTerm;
import java.lang.Class;
import java.lang.Exception;
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
public final class GlossaryDao_Impl implements GlossaryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<GlossaryTerm> __insertionAdapterOfGlossaryTerm;

  public GlossaryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfGlossaryTerm = new EntityInsertionAdapter<GlossaryTerm>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `glossary_terms` (`id`,`term`,`definition`,`videoUrl`,`localExplainerPath`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GlossaryTerm entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTerm());
        statement.bindString(3, entity.getDefinition());
        if (entity.getVideoUrl() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getVideoUrl());
        }
        if (entity.getLocalExplainerPath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getLocalExplainerPath());
        }
      }
    };
  }

  @Override
  public Object insertAll(final List<GlossaryTerm> terms,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfGlossaryTerm.insert(terms);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllTerms(final Continuation<? super List<GlossaryTerm>> $completion) {
    final String _sql = "SELECT * FROM glossary_terms";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<GlossaryTerm>>() {
      @Override
      @NonNull
      public List<GlossaryTerm> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTerm = CursorUtil.getColumnIndexOrThrow(_cursor, "term");
          final int _cursorIndexOfDefinition = CursorUtil.getColumnIndexOrThrow(_cursor, "definition");
          final int _cursorIndexOfVideoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "videoUrl");
          final int _cursorIndexOfLocalExplainerPath = CursorUtil.getColumnIndexOrThrow(_cursor, "localExplainerPath");
          final List<GlossaryTerm> _result = new ArrayList<GlossaryTerm>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final GlossaryTerm _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTerm;
            _tmpTerm = _cursor.getString(_cursorIndexOfTerm);
            final String _tmpDefinition;
            _tmpDefinition = _cursor.getString(_cursorIndexOfDefinition);
            final String _tmpVideoUrl;
            if (_cursor.isNull(_cursorIndexOfVideoUrl)) {
              _tmpVideoUrl = null;
            } else {
              _tmpVideoUrl = _cursor.getString(_cursorIndexOfVideoUrl);
            }
            final String _tmpLocalExplainerPath;
            if (_cursor.isNull(_cursorIndexOfLocalExplainerPath)) {
              _tmpLocalExplainerPath = null;
            } else {
              _tmpLocalExplainerPath = _cursor.getString(_cursorIndexOfLocalExplainerPath);
            }
            _item = new GlossaryTerm(_tmpId,_tmpTerm,_tmpDefinition,_tmpVideoUrl,_tmpLocalExplainerPath);
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
  public Object findByTerm(final String term,
      final Continuation<? super GlossaryTerm> $completion) {
    final String _sql = "SELECT * FROM glossary_terms WHERE term = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, term);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<GlossaryTerm>() {
      @Override
      @Nullable
      public GlossaryTerm call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTerm = CursorUtil.getColumnIndexOrThrow(_cursor, "term");
          final int _cursorIndexOfDefinition = CursorUtil.getColumnIndexOrThrow(_cursor, "definition");
          final int _cursorIndexOfVideoUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "videoUrl");
          final int _cursorIndexOfLocalExplainerPath = CursorUtil.getColumnIndexOrThrow(_cursor, "localExplainerPath");
          final GlossaryTerm _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTerm;
            _tmpTerm = _cursor.getString(_cursorIndexOfTerm);
            final String _tmpDefinition;
            _tmpDefinition = _cursor.getString(_cursorIndexOfDefinition);
            final String _tmpVideoUrl;
            if (_cursor.isNull(_cursorIndexOfVideoUrl)) {
              _tmpVideoUrl = null;
            } else {
              _tmpVideoUrl = _cursor.getString(_cursorIndexOfVideoUrl);
            }
            final String _tmpLocalExplainerPath;
            if (_cursor.isNull(_cursorIndexOfLocalExplainerPath)) {
              _tmpLocalExplainerPath = null;
            } else {
              _tmpLocalExplainerPath = _cursor.getString(_cursorIndexOfLocalExplainerPath);
            }
            _result = new GlossaryTerm(_tmpId,_tmpTerm,_tmpDefinition,_tmpVideoUrl,_tmpLocalExplainerPath);
          } else {
            _result = null;
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
