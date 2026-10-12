package com.trypod33.kitchlore.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.trypod33.kitchlore.data.local.entity.Recipe;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class RecipeDao_Impl implements RecipeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Recipe> __insertionAdapterOfRecipe;

  private final EntityDeletionOrUpdateAdapter<Recipe> __deletionAdapterOfRecipe;

  private final EntityDeletionOrUpdateAdapter<Recipe> __updateAdapterOfRecipe;

  public RecipeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRecipe = new EntityInsertionAdapter<Recipe>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `recipes` (`id`,`title`,`baseServings`,`sourceType`,`sourceNote`,`originalImagePath`,`notes`,`prepTimeMinutes`,`cookTimeMinutes`,`dateAdded`,`dateModified`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Recipe entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindLong(3, entity.getBaseServings());
        statement.bindString(4, entity.getSourceType());
        if (entity.getSourceNote() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSourceNote());
        }
        if (entity.getOriginalImagePath() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getOriginalImagePath());
        }
        if (entity.getNotes() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getNotes());
        }
        if (entity.getPrepTimeMinutes() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getPrepTimeMinutes());
        }
        if (entity.getCookTimeMinutes() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getCookTimeMinutes());
        }
        statement.bindLong(10, entity.getDateAdded());
        statement.bindLong(11, entity.getDateModified());
      }
    };
    this.__deletionAdapterOfRecipe = new EntityDeletionOrUpdateAdapter<Recipe>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `recipes` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Recipe entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfRecipe = new EntityDeletionOrUpdateAdapter<Recipe>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `recipes` SET `id` = ?,`title` = ?,`baseServings` = ?,`sourceType` = ?,`sourceNote` = ?,`originalImagePath` = ?,`notes` = ?,`prepTimeMinutes` = ?,`cookTimeMinutes` = ?,`dateAdded` = ?,`dateModified` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Recipe entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindLong(3, entity.getBaseServings());
        statement.bindString(4, entity.getSourceType());
        if (entity.getSourceNote() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSourceNote());
        }
        if (entity.getOriginalImagePath() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getOriginalImagePath());
        }
        if (entity.getNotes() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getNotes());
        }
        if (entity.getPrepTimeMinutes() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getPrepTimeMinutes());
        }
        if (entity.getCookTimeMinutes() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getCookTimeMinutes());
        }
        statement.bindLong(10, entity.getDateAdded());
        statement.bindLong(11, entity.getDateModified());
        statement.bindLong(12, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final Recipe recipe, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfRecipe.insertAndReturnId(recipe);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final Recipe recipe, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfRecipe.handle(recipe);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final Recipe recipe, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfRecipe.handle(recipe);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Recipe>> getAllRecipes() {
    final String _sql = "SELECT * FROM recipes ORDER BY dateModified DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"recipes"}, new Callable<List<Recipe>>() {
      @Override
      @NonNull
      public List<Recipe> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfBaseServings = CursorUtil.getColumnIndexOrThrow(_cursor, "baseServings");
          final int _cursorIndexOfSourceType = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceType");
          final int _cursorIndexOfSourceNote = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceNote");
          final int _cursorIndexOfOriginalImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "originalImagePath");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfPrepTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "prepTimeMinutes");
          final int _cursorIndexOfCookTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "cookTimeMinutes");
          final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
          final int _cursorIndexOfDateModified = CursorUtil.getColumnIndexOrThrow(_cursor, "dateModified");
          final List<Recipe> _result = new ArrayList<Recipe>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Recipe _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpBaseServings;
            _tmpBaseServings = _cursor.getInt(_cursorIndexOfBaseServings);
            final String _tmpSourceType;
            _tmpSourceType = _cursor.getString(_cursorIndexOfSourceType);
            final String _tmpSourceNote;
            if (_cursor.isNull(_cursorIndexOfSourceNote)) {
              _tmpSourceNote = null;
            } else {
              _tmpSourceNote = _cursor.getString(_cursorIndexOfSourceNote);
            }
            final String _tmpOriginalImagePath;
            if (_cursor.isNull(_cursorIndexOfOriginalImagePath)) {
              _tmpOriginalImagePath = null;
            } else {
              _tmpOriginalImagePath = _cursor.getString(_cursorIndexOfOriginalImagePath);
            }
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            final Integer _tmpPrepTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfPrepTimeMinutes)) {
              _tmpPrepTimeMinutes = null;
            } else {
              _tmpPrepTimeMinutes = _cursor.getInt(_cursorIndexOfPrepTimeMinutes);
            }
            final Integer _tmpCookTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfCookTimeMinutes)) {
              _tmpCookTimeMinutes = null;
            } else {
              _tmpCookTimeMinutes = _cursor.getInt(_cursorIndexOfCookTimeMinutes);
            }
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            final long _tmpDateModified;
            _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
            _item = new Recipe(_tmpId,_tmpTitle,_tmpBaseServings,_tmpSourceType,_tmpSourceNote,_tmpOriginalImagePath,_tmpNotes,_tmpPrepTimeMinutes,_tmpCookTimeMinutes,_tmpDateAdded,_tmpDateModified);
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
  public Object getRecipeById(final long recipeId, final Continuation<? super Recipe> $completion) {
    final String _sql = "SELECT * FROM recipes WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, recipeId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Recipe>() {
      @Override
      @Nullable
      public Recipe call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfBaseServings = CursorUtil.getColumnIndexOrThrow(_cursor, "baseServings");
          final int _cursorIndexOfSourceType = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceType");
          final int _cursorIndexOfSourceNote = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceNote");
          final int _cursorIndexOfOriginalImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "originalImagePath");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfPrepTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "prepTimeMinutes");
          final int _cursorIndexOfCookTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "cookTimeMinutes");
          final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
          final int _cursorIndexOfDateModified = CursorUtil.getColumnIndexOrThrow(_cursor, "dateModified");
          final Recipe _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpBaseServings;
            _tmpBaseServings = _cursor.getInt(_cursorIndexOfBaseServings);
            final String _tmpSourceType;
            _tmpSourceType = _cursor.getString(_cursorIndexOfSourceType);
            final String _tmpSourceNote;
            if (_cursor.isNull(_cursorIndexOfSourceNote)) {
              _tmpSourceNote = null;
            } else {
              _tmpSourceNote = _cursor.getString(_cursorIndexOfSourceNote);
            }
            final String _tmpOriginalImagePath;
            if (_cursor.isNull(_cursorIndexOfOriginalImagePath)) {
              _tmpOriginalImagePath = null;
            } else {
              _tmpOriginalImagePath = _cursor.getString(_cursorIndexOfOriginalImagePath);
            }
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            final Integer _tmpPrepTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfPrepTimeMinutes)) {
              _tmpPrepTimeMinutes = null;
            } else {
              _tmpPrepTimeMinutes = _cursor.getInt(_cursorIndexOfPrepTimeMinutes);
            }
            final Integer _tmpCookTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfCookTimeMinutes)) {
              _tmpCookTimeMinutes = null;
            } else {
              _tmpCookTimeMinutes = _cursor.getInt(_cursorIndexOfCookTimeMinutes);
            }
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            final long _tmpDateModified;
            _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
            _result = new Recipe(_tmpId,_tmpTitle,_tmpBaseServings,_tmpSourceType,_tmpSourceNote,_tmpOriginalImagePath,_tmpNotes,_tmpPrepTimeMinutes,_tmpCookTimeMinutes,_tmpDateAdded,_tmpDateModified);
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
  public Object getAllRecipesOnce(final Continuation<? super List<Recipe>> $completion) {
    final String _sql = "SELECT * FROM recipes ORDER BY dateModified DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Recipe>>() {
      @Override
      @NonNull
      public List<Recipe> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfBaseServings = CursorUtil.getColumnIndexOrThrow(_cursor, "baseServings");
          final int _cursorIndexOfSourceType = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceType");
          final int _cursorIndexOfSourceNote = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceNote");
          final int _cursorIndexOfOriginalImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "originalImagePath");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfPrepTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "prepTimeMinutes");
          final int _cursorIndexOfCookTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "cookTimeMinutes");
          final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
          final int _cursorIndexOfDateModified = CursorUtil.getColumnIndexOrThrow(_cursor, "dateModified");
          final List<Recipe> _result = new ArrayList<Recipe>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Recipe _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpBaseServings;
            _tmpBaseServings = _cursor.getInt(_cursorIndexOfBaseServings);
            final String _tmpSourceType;
            _tmpSourceType = _cursor.getString(_cursorIndexOfSourceType);
            final String _tmpSourceNote;
            if (_cursor.isNull(_cursorIndexOfSourceNote)) {
              _tmpSourceNote = null;
            } else {
              _tmpSourceNote = _cursor.getString(_cursorIndexOfSourceNote);
            }
            final String _tmpOriginalImagePath;
            if (_cursor.isNull(_cursorIndexOfOriginalImagePath)) {
              _tmpOriginalImagePath = null;
            } else {
              _tmpOriginalImagePath = _cursor.getString(_cursorIndexOfOriginalImagePath);
            }
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            final Integer _tmpPrepTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfPrepTimeMinutes)) {
              _tmpPrepTimeMinutes = null;
            } else {
              _tmpPrepTimeMinutes = _cursor.getInt(_cursorIndexOfPrepTimeMinutes);
            }
            final Integer _tmpCookTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfCookTimeMinutes)) {
              _tmpCookTimeMinutes = null;
            } else {
              _tmpCookTimeMinutes = _cursor.getInt(_cursorIndexOfCookTimeMinutes);
            }
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            final long _tmpDateModified;
            _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
            _item = new Recipe(_tmpId,_tmpTitle,_tmpBaseServings,_tmpSourceType,_tmpSourceNote,_tmpOriginalImagePath,_tmpNotes,_tmpPrepTimeMinutes,_tmpCookTimeMinutes,_tmpDateAdded,_tmpDateModified);
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
  public Flow<List<Recipe>> searchByTitle(final String query) {
    final String _sql = "SELECT * FROM recipes WHERE title LIKE '%' || ? || '%'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, query);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"recipes"}, new Callable<List<Recipe>>() {
      @Override
      @NonNull
      public List<Recipe> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfBaseServings = CursorUtil.getColumnIndexOrThrow(_cursor, "baseServings");
          final int _cursorIndexOfSourceType = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceType");
          final int _cursorIndexOfSourceNote = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceNote");
          final int _cursorIndexOfOriginalImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "originalImagePath");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfPrepTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "prepTimeMinutes");
          final int _cursorIndexOfCookTimeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "cookTimeMinutes");
          final int _cursorIndexOfDateAdded = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAdded");
          final int _cursorIndexOfDateModified = CursorUtil.getColumnIndexOrThrow(_cursor, "dateModified");
          final List<Recipe> _result = new ArrayList<Recipe>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Recipe _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpBaseServings;
            _tmpBaseServings = _cursor.getInt(_cursorIndexOfBaseServings);
            final String _tmpSourceType;
            _tmpSourceType = _cursor.getString(_cursorIndexOfSourceType);
            final String _tmpSourceNote;
            if (_cursor.isNull(_cursorIndexOfSourceNote)) {
              _tmpSourceNote = null;
            } else {
              _tmpSourceNote = _cursor.getString(_cursorIndexOfSourceNote);
            }
            final String _tmpOriginalImagePath;
            if (_cursor.isNull(_cursorIndexOfOriginalImagePath)) {
              _tmpOriginalImagePath = null;
            } else {
              _tmpOriginalImagePath = _cursor.getString(_cursorIndexOfOriginalImagePath);
            }
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            final Integer _tmpPrepTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfPrepTimeMinutes)) {
              _tmpPrepTimeMinutes = null;
            } else {
              _tmpPrepTimeMinutes = _cursor.getInt(_cursorIndexOfPrepTimeMinutes);
            }
            final Integer _tmpCookTimeMinutes;
            if (_cursor.isNull(_cursorIndexOfCookTimeMinutes)) {
              _tmpCookTimeMinutes = null;
            } else {
              _tmpCookTimeMinutes = _cursor.getInt(_cursorIndexOfCookTimeMinutes);
            }
            final long _tmpDateAdded;
            _tmpDateAdded = _cursor.getLong(_cursorIndexOfDateAdded);
            final long _tmpDateModified;
            _tmpDateModified = _cursor.getLong(_cursorIndexOfDateModified);
            _item = new Recipe(_tmpId,_tmpTitle,_tmpBaseServings,_tmpSourceType,_tmpSourceNote,_tmpOriginalImagePath,_tmpNotes,_tmpPrepTimeMinutes,_tmpCookTimeMinutes,_tmpDateAdded,_tmpDateModified);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
