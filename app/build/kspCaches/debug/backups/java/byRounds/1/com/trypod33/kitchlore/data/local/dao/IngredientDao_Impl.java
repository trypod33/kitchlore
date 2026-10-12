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
import com.trypod33.kitchlore.data.local.entity.Ingredient;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Long;
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
public final class IngredientDao_Impl implements IngredientDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Ingredient> __insertionAdapterOfIngredient;

  private final EntityDeletionOrUpdateAdapter<Ingredient> __deletionAdapterOfIngredient;

  private final EntityDeletionOrUpdateAdapter<Ingredient> __updateAdapterOfIngredient;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllForRecipe;

  public IngredientDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfIngredient = new EntityInsertionAdapter<Ingredient>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `ingredients` (`id`,`recipeId`,`name`,`rawText`,`amount`,`unit`,`sortOrder`,`matchedNutritionId`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Ingredient entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRecipeId());
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getRawText());
        if (entity.getAmount() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getAmount());
        }
        if (entity.getUnit() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getUnit());
        }
        statement.bindLong(7, entity.getSortOrder());
        if (entity.getMatchedNutritionId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getMatchedNutritionId());
        }
      }
    };
    this.__deletionAdapterOfIngredient = new EntityDeletionOrUpdateAdapter<Ingredient>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `ingredients` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Ingredient entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfIngredient = new EntityDeletionOrUpdateAdapter<Ingredient>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `ingredients` SET `id` = ?,`recipeId` = ?,`name` = ?,`rawText` = ?,`amount` = ?,`unit` = ?,`sortOrder` = ?,`matchedNutritionId` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Ingredient entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getRecipeId());
        statement.bindString(3, entity.getName());
        statement.bindString(4, entity.getRawText());
        if (entity.getAmount() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getAmount());
        }
        if (entity.getUnit() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getUnit());
        }
        statement.bindLong(7, entity.getSortOrder());
        if (entity.getMatchedNutritionId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getMatchedNutritionId());
        }
        statement.bindLong(9, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAllForRecipe = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM ingredients WHERE recipeId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<Ingredient> ingredients,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfIngredient.insert(ingredients);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final Ingredient ingredient, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfIngredient.handle(ingredient);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final Ingredient ingredient, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfIngredient.handle(ingredient);
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
  public Flow<List<Ingredient>> getIngredientsForRecipe(final long recipeId) {
    final String _sql = "SELECT * FROM ingredients WHERE recipeId = ? ORDER BY sortOrder ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, recipeId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"ingredients"}, new Callable<List<Ingredient>>() {
      @Override
      @NonNull
      public List<Ingredient> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRecipeId = CursorUtil.getColumnIndexOrThrow(_cursor, "recipeId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawText");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "unit");
          final int _cursorIndexOfSortOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "sortOrder");
          final int _cursorIndexOfMatchedNutritionId = CursorUtil.getColumnIndexOrThrow(_cursor, "matchedNutritionId");
          final List<Ingredient> _result = new ArrayList<Ingredient>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Ingredient _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRecipeId;
            _tmpRecipeId = _cursor.getLong(_cursorIndexOfRecipeId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final Double _tmpAmount;
            if (_cursor.isNull(_cursorIndexOfAmount)) {
              _tmpAmount = null;
            } else {
              _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            }
            final String _tmpUnit;
            if (_cursor.isNull(_cursorIndexOfUnit)) {
              _tmpUnit = null;
            } else {
              _tmpUnit = _cursor.getString(_cursorIndexOfUnit);
            }
            final int _tmpSortOrder;
            _tmpSortOrder = _cursor.getInt(_cursorIndexOfSortOrder);
            final Long _tmpMatchedNutritionId;
            if (_cursor.isNull(_cursorIndexOfMatchedNutritionId)) {
              _tmpMatchedNutritionId = null;
            } else {
              _tmpMatchedNutritionId = _cursor.getLong(_cursorIndexOfMatchedNutritionId);
            }
            _item = new Ingredient(_tmpId,_tmpRecipeId,_tmpName,_tmpRawText,_tmpAmount,_tmpUnit,_tmpSortOrder,_tmpMatchedNutritionId);
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
  public Object getIngredientsForRecipeOnce(final long recipeId,
      final Continuation<? super List<Ingredient>> $completion) {
    final String _sql = "SELECT * FROM ingredients WHERE recipeId = ? ORDER BY sortOrder ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, recipeId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Ingredient>>() {
      @Override
      @NonNull
      public List<Ingredient> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfRecipeId = CursorUtil.getColumnIndexOrThrow(_cursor, "recipeId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRawText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawText");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "unit");
          final int _cursorIndexOfSortOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "sortOrder");
          final int _cursorIndexOfMatchedNutritionId = CursorUtil.getColumnIndexOrThrow(_cursor, "matchedNutritionId");
          final List<Ingredient> _result = new ArrayList<Ingredient>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Ingredient _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpRecipeId;
            _tmpRecipeId = _cursor.getLong(_cursorIndexOfRecipeId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRawText;
            _tmpRawText = _cursor.getString(_cursorIndexOfRawText);
            final Double _tmpAmount;
            if (_cursor.isNull(_cursorIndexOfAmount)) {
              _tmpAmount = null;
            } else {
              _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            }
            final String _tmpUnit;
            if (_cursor.isNull(_cursorIndexOfUnit)) {
              _tmpUnit = null;
            } else {
              _tmpUnit = _cursor.getString(_cursorIndexOfUnit);
            }
            final int _tmpSortOrder;
            _tmpSortOrder = _cursor.getInt(_cursorIndexOfSortOrder);
            final Long _tmpMatchedNutritionId;
            if (_cursor.isNull(_cursorIndexOfMatchedNutritionId)) {
              _tmpMatchedNutritionId = null;
            } else {
              _tmpMatchedNutritionId = _cursor.getLong(_cursorIndexOfMatchedNutritionId);
            }
            _item = new Ingredient(_tmpId,_tmpRecipeId,_tmpName,_tmpRawText,_tmpAmount,_tmpUnit,_tmpSortOrder,_tmpMatchedNutritionId);
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
