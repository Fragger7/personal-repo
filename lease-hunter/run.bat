@echo off
cd /d "%~dp0"
if exist local_password.txt (
    set /p STREAMLIT_ACCESS_PASSWORD=<local_password.txt
)
start "" pythonw -m streamlit run app.py
exit
