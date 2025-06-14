import pandas as pd
import numpy as np
from sklearn.preprocessing import PolynomialFeatures, StandardScaler, OneHotEncoder
from sklearn.linear_model import LinearRegression
from sklearn.pipeline import make_pipeline
from sklearn.compose import ColumnTransformer
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error, r2_score
import re
import json

# Load data
df = pd.read_csv("../results.csv")

def extract_parameters(df):
    """Extract all parameters from the parameters column"""
    games = {
        'FifteenPuzzle': ['shuffles', 'size'],
        'CaptureTheFlag': ['gridSize', 'numBlueAgents', 'numRedAgents', 'flagPlacementType'],
        'GameOfLife': ['iterations', 'mapFilename']
    }
    
    results = []
    for _, row in df.iterrows():
        params = {}
        game = row['game']
        param_str = row['parameters']
        
        for param in games.get(game, []):
            if param == 'mapFilename':
                match = re.search(r'mapFilename=([^#]+)', param_str)
                if match:
                    params[param] = match.group(1)
            elif param == 'flagPlacementType':
                match = re.search(r'flagPlacementType=([A-Z])', param_str)
                if match:
                    params[param] = match.group(1)
            else:
                match = re.search(fr'{param}=(\d+)', param_str)
                if match:
                    params[param] = int(match.group(1))
        
        results.append(params)
    
    return pd.DataFrame(results)

def expand_feature_name(feature):
    parts = feature.split(" ")
    return "*".join(parts)

def evaluate_model(model, X_train, y_train, X_test, y_test):
    y_train_pred = model.predict(X_train)
    y_test_pred = model.predict(X_test)
    
    return {
        'train_r2': r2_score(y_train, y_train_pred),
        'test_r2': r2_score(y_test, y_test_pred),
        'test_mse': mean_squared_error(y_test, y_test_pred),
        'test_rmse': np.sqrt(mean_squared_error(y_test, y_test_pred))
    }

def print_metrics(game_name, metrics, equation):
    print(f"\n{game_name} Results:")
    print("Equation:", equation)
    print(f"Train R²: {metrics['train_r2']:.3f}")
    print(f"Test R²:  {metrics['test_r2']:.3f}")
    print(f"Test MSE: {metrics['test_mse']:.3f}")
    print(f"Test RMSE: {metrics['test_rmse']:.3f}")

# Load and extract parameters
params_df = extract_parameters(df)
df = pd.concat([df, params_df], axis=1)

# Dictionary to collect all model details
complexity_estimators = {}

# =============================================
# 1. FifteenPuzzle (Polynomial Regression)
# =============================================
fifteen_df = df[df["game"] == "FifteenPuzzle"].copy()
if not fifteen_df.empty:
    X = fifteen_df[["shuffles", "size"]]
    y = np.log1p(fifteen_df["complexity"])
    
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)
    
    X_train, X_test, y_train, y_test = train_test_split(X_scaled, y, test_size=0.2, random_state=42)

    model = make_pipeline(
        PolynomialFeatures(degree=3, include_bias=False),
        LinearRegression()
    )
    model.fit(X_train, y_train)

    metrics = evaluate_model(model, X_train, y_train, X_test, y_test)
    
    raw_feature_names = model.named_steps['polynomialfeatures'].get_feature_names_out(X.columns)
    feature_names = [expand_feature_name(f) for f in raw_feature_names]
    coefs = model.named_steps['linearregression'].coef_.tolist()
    intercept = float(model.named_steps['linearregression'].intercept_)
    
    equation = f"log(complexity) = {intercept:.6f}"
    for coef, name in zip(coefs, feature_names):
        equation += f" + ({coef:.6f})*{name}"
    equation += "\ncomplexity = exp(log(complexity))"

    complexity_estimators["FifteenPuzzle"] = {
        "intercept": intercept,
        "coefficients": coefs,
        "feature_names": feature_names,
        "is_log_transformed": True,
        "scaler_params": {
            "mean": scaler.mean_.tolist(),
            "scale": scaler.scale_.tolist()
        },
        "metrics": metrics
    }

    print_metrics("FifteenPuzzle (Polynomial Regression)", metrics, equation)

# =============================================
# 2. CaptureTheFlag (Polynomial Regression)
# =============================================
ctf_df = df[df["game"] == "CaptureTheFlag"].copy().dropna(subset=["gridSize", "numBlueAgents", "numRedAgents", "flagPlacementType"])
if not ctf_df.empty:
    X = ctf_df[["gridSize", "numBlueAgents", "numRedAgents", "flagPlacementType"]]
    y = np.log1p(ctf_df["complexity"])
    
    preprocessor = ColumnTransformer([
        ("num", StandardScaler(), ["gridSize", "numBlueAgents", "numRedAgents"]),
        ("cat", OneHotEncoder(drop="first"), ["flagPlacementType"])
    ])
    
    model = make_pipeline(
        preprocessor,
        PolynomialFeatures(degree=2, include_bias=False),
        LinearRegression()
    )

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    model.fit(X_train, y_train)
    
    metrics = evaluate_model(model, X_train, y_train, X_test, y_test)
    
    num_features = ["gridSize", "numBlueAgents", "numRedAgents"]
    cat_features = [f"flagPlacementType_{x}" for x in ['B', 'C']]  # 'A' dropped
    raw_feature_names = model.named_steps['polynomialfeatures'].get_feature_names_out(num_features + cat_features)
    feature_names = [expand_feature_name(f) for f in raw_feature_names]
    
    coefs = model.named_steps['linearregression'].coef_.tolist()
    intercept = float(model.named_steps['linearregression'].intercept_)

    equation = f"log(complexity) = {intercept:.6f}"
    for coef, name in zip(coefs, feature_names):
        equation += f" + ({coef:.6f})*{name}"
    equation += "\ncomplexity = exp(log(complexity))"

    complexity_estimators["CaptureTheFlag"] = {
        "intercept": intercept,
        "coefficients": coefs,
        "feature_names": feature_names,
        "is_log_transformed": True,
        "scaler_params": {
            "mean": preprocessor.named_transformers_['num'].mean_.tolist(),
            "scale": preprocessor.named_transformers_['num'].scale_.tolist()
        },
        "metrics": metrics
    }

    print_metrics("CaptureTheFlag (Polynomial Regression)", metrics, equation)

# =============================================
# 3. GameOfLife (Linear Regression)
# =============================================
gol_df = df[df["game"] == "GameOfLife"].copy().dropna(subset=["iterations"])
if not gol_df.empty:
    X = gol_df[["iterations"]]
    y = gol_df["complexity"]
    
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    model = LinearRegression()
    model.fit(X_train, y_train)

    metrics = evaluate_model(model, X_train, y_train, X_test, y_test)
    
    coef = float(model.coef_[0])
    intercept = float(model.intercept_)
    feature_names = ["iterations"]
    coefs = [coef]

    equation = f"complexity = {intercept:.6f} + ({coef:.6f})*iterations"

    complexity_estimators["GameOfLife"] = {
        "intercept": intercept,
        "coefficients": coefs,
        "feature_names": feature_names,
        "is_log_transformed": False,
        "scaler_params": None,
        "metrics": metrics
    }

    print_metrics("GameOfLife", metrics, equation)

# =============================================
# Save all equations to one JSON file
# =============================================
with open('complexity_estimators.json', 'w') as f:
    json.dump(complexity_estimators, f, indent=4)

print("\nSaved all equations to 'complexity_estimators.json'")
