package com.trypod33.kitchlore.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.trypod33.kitchlore.data.local.dao.GlossaryDao;
import com.trypod33.kitchlore.data.local.dao.GlossaryDao_Impl;
import com.trypod33.kitchlore.data.local.dao.IngredientDao;
import com.trypod33.kitchlore.data.local.dao.IngredientDao_Impl;
import com.trypod33.kitchlore.data.local.dao.NutritionDao;
import com.trypod33.kitchlore.data.local.dao.NutritionDao_Impl;
import com.trypod33.kitchlore.data.local.dao.RecipeDao;
import com.trypod33.kitchlore.data.local.dao.RecipeDao_Impl;
import com.trypod33.kitchlore.data.local.dao.StepDao;
import com.trypod33.kitchlore.data.local.dao.StepDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile RecipeDao _recipeDao;

  private volatile IngredientDao _ingredientDao;

  private volatile StepDao _stepDao;

  private volatile NutritionDao _nutritionDao;

  private volatile GlossaryDao _glossaryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `baseServings` INTEGER NOT NULL, `sourceType` TEXT NOT NULL, `sourceNote` TEXT, `originalImagePath` TEXT, `notes` TEXT, `prepTimeMinutes` INTEGER, `cookTimeMinutes` INTEGER, `dateAdded` INTEGER NOT NULL, `dateModified` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `ingredients` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `name` TEXT NOT NULL, `rawText` TEXT NOT NULL, `amount` REAL, `unit` TEXT, `sortOrder` INTEGER NOT NULL, `matchedNutritionId` INTEGER, FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredients_recipeId` ON `ingredients` (`recipeId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredients_matchedNutritionId` ON `ingredients` (`matchedNutritionId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `steps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recipeId` INTEGER NOT NULL, `stepNumber` INTEGER NOT NULL, `instructionText` TEXT NOT NULL, `timerSeconds` INTEGER, FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_steps_recipeId` ON `steps` (`recipeId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `nutrition_reference` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `foodName` TEXT NOT NULL, `caloriesPer100g` REAL NOT NULL, `proteinGPer100g` REAL NOT NULL, `fatGPer100g` REAL NOT NULL, `carbsGPer100g` REAL NOT NULL, `fiberGPer100g` REAL NOT NULL, `sugarGPer100g` REAL NOT NULL, `sodiumMgPer100g` REAL NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `glossary_terms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `term` TEXT NOT NULL, `definition` TEXT NOT NULL, `videoUrl` TEXT, `localExplainerPath` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e3896504ca8037c8dbe03825ca4b5ed4')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `recipes`");
        db.execSQL("DROP TABLE IF EXISTS `ingredients`");
        db.execSQL("DROP TABLE IF EXISTS `steps`");
        db.execSQL("DROP TABLE IF EXISTS `nutrition_reference`");
        db.execSQL("DROP TABLE IF EXISTS `glossary_terms`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsRecipes = new HashMap<String, TableInfo.Column>(11);
        _columnsRecipes.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("baseServings", new TableInfo.Column("baseServings", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("sourceType", new TableInfo.Column("sourceType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("sourceNote", new TableInfo.Column("sourceNote", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("originalImagePath", new TableInfo.Column("originalImagePath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("notes", new TableInfo.Column("notes", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("prepTimeMinutes", new TableInfo.Column("prepTimeMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("cookTimeMinutes", new TableInfo.Column("cookTimeMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("dateAdded", new TableInfo.Column("dateAdded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsRecipes.put("dateModified", new TableInfo.Column("dateModified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysRecipes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesRecipes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoRecipes = new TableInfo("recipes", _columnsRecipes, _foreignKeysRecipes, _indicesRecipes);
        final TableInfo _existingRecipes = TableInfo.read(db, "recipes");
        if (!_infoRecipes.equals(_existingRecipes)) {
          return new RoomOpenHelper.ValidationResult(false, "recipes(com.trypod33.kitchlore.data.local.entity.Recipe).\n"
                  + " Expected:\n" + _infoRecipes + "\n"
                  + " Found:\n" + _existingRecipes);
        }
        final HashMap<String, TableInfo.Column> _columnsIngredients = new HashMap<String, TableInfo.Column>(8);
        _columnsIngredients.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsIngredients.put("recipeId", new TableInfo.Column("recipeId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsIngredients.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsIngredients.put("rawText", new TableInfo.Column("rawText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsIngredients.put("amount", new TableInfo.Column("amount", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsIngredients.put("unit", new TableInfo.Column("unit", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsIngredients.put("sortOrder", new TableInfo.Column("sortOrder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsIngredients.put("matchedNutritionId", new TableInfo.Column("matchedNutritionId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysIngredients = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysIngredients.add(new TableInfo.ForeignKey("recipes", "CASCADE", "NO ACTION", Arrays.asList("recipeId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesIngredients = new HashSet<TableInfo.Index>(2);
        _indicesIngredients.add(new TableInfo.Index("index_ingredients_recipeId", false, Arrays.asList("recipeId"), Arrays.asList("ASC")));
        _indicesIngredients.add(new TableInfo.Index("index_ingredients_matchedNutritionId", false, Arrays.asList("matchedNutritionId"), Arrays.asList("ASC")));
        final TableInfo _infoIngredients = new TableInfo("ingredients", _columnsIngredients, _foreignKeysIngredients, _indicesIngredients);
        final TableInfo _existingIngredients = TableInfo.read(db, "ingredients");
        if (!_infoIngredients.equals(_existingIngredients)) {
          return new RoomOpenHelper.ValidationResult(false, "ingredients(com.trypod33.kitchlore.data.local.entity.Ingredient).\n"
                  + " Expected:\n" + _infoIngredients + "\n"
                  + " Found:\n" + _existingIngredients);
        }
        final HashMap<String, TableInfo.Column> _columnsSteps = new HashMap<String, TableInfo.Column>(5);
        _columnsSteps.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSteps.put("recipeId", new TableInfo.Column("recipeId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSteps.put("stepNumber", new TableInfo.Column("stepNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSteps.put("instructionText", new TableInfo.Column("instructionText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSteps.put("timerSeconds", new TableInfo.Column("timerSeconds", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSteps = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysSteps.add(new TableInfo.ForeignKey("recipes", "CASCADE", "NO ACTION", Arrays.asList("recipeId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesSteps = new HashSet<TableInfo.Index>(1);
        _indicesSteps.add(new TableInfo.Index("index_steps_recipeId", false, Arrays.asList("recipeId"), Arrays.asList("ASC")));
        final TableInfo _infoSteps = new TableInfo("steps", _columnsSteps, _foreignKeysSteps, _indicesSteps);
        final TableInfo _existingSteps = TableInfo.read(db, "steps");
        if (!_infoSteps.equals(_existingSteps)) {
          return new RoomOpenHelper.ValidationResult(false, "steps(com.trypod33.kitchlore.data.local.entity.Step).\n"
                  + " Expected:\n" + _infoSteps + "\n"
                  + " Found:\n" + _existingSteps);
        }
        final HashMap<String, TableInfo.Column> _columnsNutritionReference = new HashMap<String, TableInfo.Column>(9);
        _columnsNutritionReference.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("foodName", new TableInfo.Column("foodName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("caloriesPer100g", new TableInfo.Column("caloriesPer100g", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("proteinGPer100g", new TableInfo.Column("proteinGPer100g", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("fatGPer100g", new TableInfo.Column("fatGPer100g", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("carbsGPer100g", new TableInfo.Column("carbsGPer100g", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("fiberGPer100g", new TableInfo.Column("fiberGPer100g", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("sugarGPer100g", new TableInfo.Column("sugarGPer100g", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsNutritionReference.put("sodiumMgPer100g", new TableInfo.Column("sodiumMgPer100g", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysNutritionReference = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesNutritionReference = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoNutritionReference = new TableInfo("nutrition_reference", _columnsNutritionReference, _foreignKeysNutritionReference, _indicesNutritionReference);
        final TableInfo _existingNutritionReference = TableInfo.read(db, "nutrition_reference");
        if (!_infoNutritionReference.equals(_existingNutritionReference)) {
          return new RoomOpenHelper.ValidationResult(false, "nutrition_reference(com.trypod33.kitchlore.data.local.entity.NutritionReference).\n"
                  + " Expected:\n" + _infoNutritionReference + "\n"
                  + " Found:\n" + _existingNutritionReference);
        }
        final HashMap<String, TableInfo.Column> _columnsGlossaryTerms = new HashMap<String, TableInfo.Column>(5);
        _columnsGlossaryTerms.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGlossaryTerms.put("term", new TableInfo.Column("term", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGlossaryTerms.put("definition", new TableInfo.Column("definition", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGlossaryTerms.put("videoUrl", new TableInfo.Column("videoUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGlossaryTerms.put("localExplainerPath", new TableInfo.Column("localExplainerPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGlossaryTerms = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGlossaryTerms = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoGlossaryTerms = new TableInfo("glossary_terms", _columnsGlossaryTerms, _foreignKeysGlossaryTerms, _indicesGlossaryTerms);
        final TableInfo _existingGlossaryTerms = TableInfo.read(db, "glossary_terms");
        if (!_infoGlossaryTerms.equals(_existingGlossaryTerms)) {
          return new RoomOpenHelper.ValidationResult(false, "glossary_terms(com.trypod33.kitchlore.data.local.entity.GlossaryTerm).\n"
                  + " Expected:\n" + _infoGlossaryTerms + "\n"
                  + " Found:\n" + _existingGlossaryTerms);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "e3896504ca8037c8dbe03825ca4b5ed4", "a0b17231303966b0be45e504c8edc87f");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "recipes","ingredients","steps","nutrition_reference","glossary_terms");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `recipes`");
      _db.execSQL("DELETE FROM `ingredients`");
      _db.execSQL("DELETE FROM `steps`");
      _db.execSQL("DELETE FROM `nutrition_reference`");
      _db.execSQL("DELETE FROM `glossary_terms`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(RecipeDao.class, RecipeDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(IngredientDao.class, IngredientDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(StepDao.class, StepDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(NutritionDao.class, NutritionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(GlossaryDao.class, GlossaryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public RecipeDao recipeDao() {
    if (_recipeDao != null) {
      return _recipeDao;
    } else {
      synchronized(this) {
        if(_recipeDao == null) {
          _recipeDao = new RecipeDao_Impl(this);
        }
        return _recipeDao;
      }
    }
  }

  @Override
  public IngredientDao ingredientDao() {
    if (_ingredientDao != null) {
      return _ingredientDao;
    } else {
      synchronized(this) {
        if(_ingredientDao == null) {
          _ingredientDao = new IngredientDao_Impl(this);
        }
        return _ingredientDao;
      }
    }
  }

  @Override
  public StepDao stepDao() {
    if (_stepDao != null) {
      return _stepDao;
    } else {
      synchronized(this) {
        if(_stepDao == null) {
          _stepDao = new StepDao_Impl(this);
        }
        return _stepDao;
      }
    }
  }

  @Override
  public NutritionDao nutritionDao() {
    if (_nutritionDao != null) {
      return _nutritionDao;
    } else {
      synchronized(this) {
        if(_nutritionDao == null) {
          _nutritionDao = new NutritionDao_Impl(this);
        }
        return _nutritionDao;
      }
    }
  }

  @Override
  public GlossaryDao glossaryDao() {
    if (_glossaryDao != null) {
      return _glossaryDao;
    } else {
      synchronized(this) {
        if(_glossaryDao == null) {
          _glossaryDao = new GlossaryDao_Impl(this);
        }
        return _glossaryDao;
      }
    }
  }
}
