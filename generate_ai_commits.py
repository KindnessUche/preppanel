# Save this file as generate_ai_commits.py
import os
import subprocess
import sys
from google import genai
from google.genai import types

# 1. Initialize the Gemini Client
api_key = os.environ.get("GEMINI_API_KEY")
if not api_key:
    print("❌ Error: GEMINI_API_KEY environment variable is not set.")
    print("Please set it using: export GEMINI_API_KEY='your_key'")
    sys.exit(1)

client = genai.Client(api_key=api_key)

def get_commit_history():
    """Gets the hash and old subject line for the last 15 commits."""
    result = subprocess.run(['git', 'log', '-n', '15', '--pretty=format:%H|||%s'], capture_output=True, text=True)
    return [line.split("|||") for line in result.stdout.strip().split("\n") if line]

def get_commit_diff(commit_hash):
    """Gets the actual code changes made in that specific commit."""
    result = subprocess.run(['git', 'show', commit_hash], capture_output=True, text=True)
    # Truncate if the diff is massive to save prompt space
    return result.stdout[:8000] 

def generate_better_message(old_message, diff_text):
    """Sends the diff to Gemini to generate a professional commit message."""
    prompt = f"""
    You are an expert software engineer. Analyze the following Git commit diff and write a highly professional, readable, and concise commit message.
    Follow the Conventional Commits specification (e.g., feat: add login validation, fix: resolve null pointer exception).
    
    Old poor message was: "{old_message}"
    
    Code changes (Diff):
    {diff_text}
    
    Respond ONLY with the final rewritten commit message. Do not include markdown blocks, quotes, or conversational text.
    """
    
    try:
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
        )
        return response.text.strip()
    except Exception as e:
        return f"Error generating message: {str(e)}"

def main():
    print("🤖 Analyzing repository history using Gemini...")
    commits = get_commit_history()
    
    if not commits:
        print("No commits found in this repository history.")
        return

    print(f"Found {len(commits)} commits to evaluate.\n" + "="*50)
    
    for commit_hash, old_msg in commits:
        print(f"📍 Analyzing Commit: {commit_hash[:7]}")
        
        diff_text = get_commit_diff(commit_hash)
        new_msg = generate_better_message(old_msg, diff_text)
        
        print(f"   ❌ Old: {old_msg}")
        print(f"   ✅ AI Suggested: {new_msg}")
        print("-" * 50)

if __name__ == "__main__":
    main()
