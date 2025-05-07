# ACU Email-based Academic Advisor Chatbot

## Overview

This project implements an AI-powered email service chatbot that acts as an Academic Advisor for Abilene Christian University. The system automatically processes incoming emails, generates responses using OpenAI's GPT-4o model, and sends replies back to students.

## Features

- **Automated Email Processing**: Monitors an inbox for new emails and processes them
- **AI-Powered Responses**: Uses OpenAI's Assistant API to generate contextual and helpful responses
- **Academic Database Integration**: Accesses course information, prerequisites, student schedules, and other academic data
- **Conversation History**: Maintains conversation context across multiple email exchanges
- **Vector Database**: Utilizes vector storage for efficient information retrieval

## Program Architecture

The program consists of several key components:

- **EmailService**: Handles email retrieval and sending operations
- **ChatbotAPI/Chatbot_API**: Interfaces with OpenAI's Assistant API
- **ConversationManager**: Tracks and maintains conversation history
- **EmailProcessor**: Orchestrates the entire process flow

## Author
Caden Finley and Alex Burgos

