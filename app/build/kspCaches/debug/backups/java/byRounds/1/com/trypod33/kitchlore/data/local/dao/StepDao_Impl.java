package com.trypod33.kitchlore.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.trypod33.kitchlore.data.local.entity.Step;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class StepDao_Impl implements StepDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Step> __insertionAdapterOfStep;

  private final EntityDeletionOrUpdateAdapter<Step> __updateAdapterOfStep;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllForRecipe;

  public StepDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfStep = new EntityInsertionAdapter<Step>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `steps` (`id`,`recipeId`,`stepNumber`,`instructionText`,`timerSeconds`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Step entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRecipeId());
        statement.bindLong(3, entity.getStepNumber());
        statement.bindString(4, entity.getInstructionText());
        if (entity.getTimerSeconds() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getTimerSeconds());
        }
      }
    };
    this.__updateAdapterOfStep = new EntityDeletionOrUpdateAdapter<Step>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `steps` SET `id` = ?,`recipeId` = ?,`stepNumber` = ?,`instructionText` = ?,`timerSeconds` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Step entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRecipeId());
        statement.bindLong(3, entity.getStepNumber());
        statement.bindString(4, entity.getInstructionText());
        if (entity.getTimerSeconds() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getTimerSeconds());
        }
        statement.bindLong(6, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAllForRecipe = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM steps WHERE recipeId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<Step> steps, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfStep.insert(steps);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final Step step, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfStep.handle(step);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllForRecipe(final long recipeId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllForRecipe.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, recipeId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllForRecipe.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Step>> getStepsForRecipe(final long recipeId) {
    final String _sql = "SELECT * FROM steps WHERE recipeId = ? ORDER BY stepNumber ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, recipeId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"steps"}, new Callable<List<Step>>() {
      @Override
      @NonNull
      public List<Step> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRecipeId = CursorUtil.getColumnIndexOrThrow(_cursor, "recipeId");
          final int _cursorIndexOfStepNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "stepNumber");
          final int _cursorIndexOfInstructionText = CursorUtil.getColumnIndexOrThrow(_cursor, "instructionText");
          final int _cursorIndexOfTimerSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "timerSeconds");
          final List<Step> _result = new ArrayList<Step>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Step _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRecipeId;
            _tmpRecipeId = _cursor.getLong(_cursorIndexOfRecipeId);
            final int _tmpStepNumber;
            _tmpStepNumber = _cursor.getInt(_cursorIndexOfStepNumber);
            final String _tmpInstructionText;
            _tmpInstructionText = _cursor.getString(_cursorIndexOfInstructionText);
            final Integer _tmpTimerSeconds;
            if (_cursor.isNull(_cursorIndexOfTimerSeconds)) {
              _tmpTimerSeconds = null;
            } else {
              _tmpTimerSeconds = _cursor.getInt(_cursorIndexOfTimerSeconds);
            }
            _item = new Step(_tmpId,_tmpRecipeId,_tmpStepNumber,_tmpInstructionText,_tmpTimerSeconds);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getStepsForRecipeOnce(final long recipeId,
      final Continuation<? super List<Step>> $completion) {
    final String _sql = "SELECT * FROM steps WHERE recipeId = ? ORDER BY stepNumber ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, recipeId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Step>>() {
      @Override
      @NonNull
      public List<Step> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRecipeId = CursorUtil.getColumnIndexOrThrow(_cursor, "recipeId");
          final int _cursorIndexOfStepNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "stepNumber");
          final int _cursorIndexOfInstructionText = CursorUtil.getColumnIndexOrThrow(_cursor, "instructionText");
          final int _cursorIndexOfTimerSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "timerSeconds");
          final List<Step> _result = new ArrayList<Step>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Step _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRecipeId;
            _tmpRecipeId = _cursor.getLong(_cursorIndexOfRecipeId);
            final int _tmpStepNumber;
            _tmpStepNumber = _cursor.getInt(_cursorIndexOfStepNumber);
            final String _tmpInstructionText;
            _tmpInstructionText = _cursor.getString(_cursorIndexOfInstructionText);
            final Integer _tmpTimerSeconds;
            if (_cursor.isNull(_cursorIndexOfTimerSeconds)) {
              _tmpTimerSeconds = null;
            } else {
              _tmpTimerSeconds = _cursor.getInt(_cursorIndexOfTimerSeconds);
            }
            _item = new Step(_tmpId,_tmpRecipeId,_tmpStepNumber,_tmpInstructionText,_tmpTimerSeconds);
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
