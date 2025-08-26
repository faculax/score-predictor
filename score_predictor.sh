#!/bin/bash
# score_predictor.sh
# Usage: ./score_predictor.sh

ODDS_API_KEY="4d83f86451e51af03077f8c5d9f2a95d"
TODAY=$(date +%Y-%m-%d)
# Calculate next day for predictions
NEXT_DAY=$(date -v+1d +%Y-%m-%d 2>/dev/null || date -d "tomorrow" +%Y-%m-%d 2>/dev/null || date +%Y-%m-%d)

echo "Fetching predictions for: $NEXT_DAY"

# Fetch Bundesliga odds for next day
DATA_FILE=$(mktemp)
curl -s "https://api.the-odds-api.com/v4/sports/soccer_germany_bundesliga/odds?regions=eu&markets=h2h&date=$NEXT_DAY&apiKey=$ODDS_API_KEY" > "$DATA_FILE"

# Call Python to process the JSON
python3 <<PYCODE
import json, math
import numpy as np

# Load JSON from file
with open("$DATA_FILE") as f:
    try:
        matches = json.load(f)
    except:
        print("No matches or invalid API response")
        exit()

def poisson_pmf(k, lam):
    return math.exp(-lam) * (lam**k) / math.factorial(k)

def score_matrix(lh, la, max_goals=8):
    ph = [poisson_pmf(i, lh) for i in range(max_goals+1)]
    pa = [poisson_pmf(j, la) for j in range(max_goals+1)]
    M = np.outer(ph, pa)
    M /= M.sum()
    return M

def probs_from_lambdas(lh, la):
    M = score_matrix(lh, la, max_goals=10)
    home = np.triu(M,1).sum()
    draw = np.trace(M)
    away = np.tril(M,-1).sum()
    return home, draw, away, M

print(f"Processing matches for {len(matches)} games...")

for match in matches:
    try:
        # Get match date and time info
        commence_time = match.get("commence_time", "")
        if commence_time:
            match_date = commence_time.split("T")[0]  # Extract date part
            match_time = commence_time.split("T")[1][:5] if "T" in commence_time else "Unknown"
        
        home = match.get("home_team")
        away = match.get("away_team")
        bookmakers = match.get("bookmakers", [])
        if not bookmakers: continue
        markets = bookmakers[0].get("markets", [])
        if not markets: continue
        outcomes = markets[0].get("outcomes", [])
        if not outcomes: continue

        home_odds = next((x["price"] for x in outcomes if x["name"]==home), None)
        draw_odds = next((x["price"] for x in outcomes if x["name"]=="Draw"), None)
        away_odds = next((x["price"] for x in outcomes if x["name"]==away), None)
        if None in (home_odds, draw_odds, away_odds): continue

        imp = [1/home_odds, 1/draw_odds, 1/away_odds]
        overround = sum(imp)
        fair = [x/overround for x in imp]
        P_home, P_draw, P_away = fair

        grid = np.linspace(0.2, 3.2, 61)
        best = (1.0, 1.0, 1e9)
        
        # Add constraint: if away team is heavy favorite, away lambda should be higher
        if P_away > 0.6:  # Away team is heavy favorite
            min_away_lambda = 1.8
            max_home_lambda = 1.2
        elif P_home > 0.6:  # Home team is heavy favorite
            min_home_lambda = 1.8
            max_away_lambda = 1.2
        else:
            min_away_lambda = 0.2
            max_home_lambda = 3.2
            min_home_lambda = 0.2
            max_away_lambda = 3.2
            
        for lh in np.linspace(0.2, max_home_lambda, 31):
            for la in np.linspace(min_away_lambda, 3.2, 31):
                ph, pd, pa, _ = probs_from_lambdas(lh, la)
                # Fix: Include away win probability in loss calculation
                loss = (ph-P_home)**2 + (pd-P_draw)**2 + (pa-P_away)**2
                if loss < best[2]:
                    best = (lh, la, loss)
        lh, la, _ = best
        _, _, _, M = probs_from_lambdas(lh, la)

        idx = np.unravel_index(np.argmax(M), M.shape)
        hg, ag = idx
        
        # Ensure predicted score matches the expected winner from odds
        if P_home > P_away:
            # Home team should win - find highest probability home win score
            home_win_scores = [(i, j, M[i,j]) for i in range(M.shape[0]) for j in range(M.shape[1]) if i > j]
            if home_win_scores:
                hg, ag, _ = max(home_win_scores, key=lambda x: x[2])
        elif P_away > P_home:
            # Away team should win - find highest probability away win score
            away_win_scores = [(i, j, M[i,j]) for i in range(M.shape[0]) for j in range(M.shape[1]) if i < j]
            if away_win_scores:
                hg, ag, _ = max(away_win_scores, key=lambda x: x[2])
        # If P_home ≈ P_away, keep the original prediction (could be draw or either team winning)
        
        # Print match info with date/time and odds
        print(f"{home} vs {away} ({match_date} {match_time})")
        print(f"  Odds: Home {home_odds:.2f}, Draw {draw_odds:.2f}, Away {away_odds:.2f}")
        print(f"  Prediction: {hg}-{ag}")
        print(f"  Win Probability: Home {P_home:.1%}, Draw {P_draw:.1%}, Away {P_away:.1%}")
        print()

    except Exception as e:
        print(f"Error processing match: {e}")
        continue
PYCODE

# Clean up
rm "$DATA_FILE"
