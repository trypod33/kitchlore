package com.trypod33.kitchlore.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import com.trypod33.kitchlore.data.local.entity.NutritionReference;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class NutritionDao_Impl implements NutritionDao {
  private final RoomDatabase __db;

  public NutritionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
  }

  @Override
  public Object findByName(final String name,
      final Continuation<? super NutritionReference> $completion) {
    final String _sql = "SELECT * FROM nutrition_reference WHERE foodName LIKE '%' || ? || '%' LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, name);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<NutritionReference>() {
      @Override
      @Nullable
      public NutritionReference call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFoodName = CursorUtil.getColumnIndexOrThrow(_cursor, "foodName");
          final int _cursorIndexOfCaloriesPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "caloriesPer100g");
          final int _cursorIndexOfProteinGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "proteinGPer100g");
          final int _cursorIndexOfFatGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "fatGPer100g");
          final int _cursorIndexOfCarbsGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "carbsGPer100g");
          final int _cursorIndexOfFiberGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "fiberGPer100g");
          final int _cursorIndexOfSugarGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "sugarGPer100g");
          final int _cursorIndexOfSodiumMgPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "sodiumMgPer100g");
          final NutritionReference _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpFoodName;
            _tmpFoodName = _cursor.getString(_cursorIndexOfFoodName);
            final double _tmpCaloriesPer100g;
            _tmpCaloriesPer100g = _cursor.getDouble(_cursorIndexOfCaloriesPer100g);
            final double _tmpProteinGPer100g;
            _tmpProteinGPer100g = _cursor.getDouble(_cursorIndexOfProteinGPer100g);
            final double _tmpFatGPer100g;
            _tmpFatGPer100g = _cursor.getDouble(_cursorIndexOfFatGPer100g);
            final double _tmpCarbsGPer100g;
            _tmpCarbsGPer100g = _cursor.getDouble(_cursorIndexOfCarbsGPer100g);
            final double _tmpFiberGPer100g;
            _tmpFiberGPer100g = _cursor.getDouble(_cursorIndexOfFiberGPer100g);
            final double _tmpSugarGPer100g;
            _tmpSugarGPer100g = _cursor.getDouble(_cursorIndexOfSugarGPer100g);
            final double _tmpSodiumMgPer100g;
            _tmpSodiumMgPer100g = _cursor.getDouble(_cursorIndexOfSodiumMgPer100g);
            _result = new NutritionReference(_tmpId,_tmpFoodName,_tmpCaloriesPer100g,_tmpProteinGPer100g,_tmpFatGPer100g,_tmpCarbsGPer100g,_tmpFiberGPer100g,_tmpSugarGPer100g,_tmpSodiumMgPer100g);
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

  @Override
  public Object getById(final long id, final Continuation<? super NutritionReference> $completion) {
    final String _sql = "SELECT * FROM nutrition_reference WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<NutritionReference>() {
      @Override
      @Nullable
      public NutritionReference call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFoodName = CursorUtil.getColumnIndexOrThrow(_cursor, "foodName");
          final int _cursorIndexOfCaloriesPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "caloriesPer100g");
          final int _cursorIndexOfProteinGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "proteinGPer100g");
          final int _cursorIndexOfFatGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "fatGPer100g");
          final int _cursorIndexOfCarbsGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "carbsGPer100g");
          final int _cursorIndexOfFiberGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "fiberGPer100g");
          final int _cursorIndexOfSugarGPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "sugarGPer100g");
          final int _cursorIndexOfSodiumMgPer100g = CursorUtil.getColumnIndexOrThrow(_cursor, "sodiumMgPer100g");
          final NutritionReference _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpFoodName;
            _tmpFoodName = _cursor.getString(_cursorIndexOfFoodName);
            final double _tmpCaloriesPer100g;
            _tmpCaloriesPer100g = _cursor.getDouble(_cursorIndexOfCaloriesPer100g);
            final double _tmpProteinGPer100g;
            _tmpProteinGPer100g = _cursor.getDouble(_cursorIndexOfProteinGPer100g);
            final double _tmpFatGPer100g;
            _tmpFatGPer100g = _cursor.getDouble(_cursorIndexOfFatGPer100g);
            final double _tmpCarbsGPer100g;
            _tmpCarbsGPer100g = _cursor.getDouble(_cursorIndexOfCarbsGPer100g);
            final double _tmpFiberGPer100g;
            _tmpFiberGPer100g = _cursor.getDouble(_cursorIndexOfFiberGPer100g);
            final double _tmpSugarGPer100g;
            _tmpSugarGPer100g = _cursor.getDouble(_cursorIndexOfSugarGPer100g);
            final double _tmpSodiumMgPer100g;
            _tmpSodiumMgPer100g = _cursor.getDouble(_cursorIndexOfSodiumMgPer100g);
            _result = new NutritionReference(_tmpId,_tmpFoodName,_tmpCaloriesPer100g,_tmpProteinGPer100g,_tmpFatGPer100g,_tmpCarbsGPer100g,_tmpFiberGPer100g,_tmpSugarGPer100g,_tmpSodiumMgPer100g);
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
