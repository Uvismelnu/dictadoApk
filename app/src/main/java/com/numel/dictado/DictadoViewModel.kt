package com.numel.dictado

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class DictadoViewModel : ViewModel() {
    val textFieldValue = mutableStateOf(TextFieldValue(""))
    val partialText = mutableStateOf("")
    val isListening = mutableStateOf(false)
    val error = mutableStateOf("")
    val modelLoaded = mutableStateOf(false)

    private var model: Model? = null
    private var speechService: SpeechService? = null

    fun initModel(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                StorageService.unpack(
                    appContext,
                    "model-es",
                    "model",
                    { loadedModel ->
                        viewModelScope.launch(Dispatchers.Main) {
                            model = loadedModel
                            modelLoaded.value = true
                            error.value = ""
                        }
                    },
                    { ioError ->
                        viewModelScope.launch(Dispatchers.Main) {
                            error.value = "Error al cargar el modelo: ${ioError.message}"
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error.value = "Error de inicialización: ${e.message}"
                }
            }
        }
    }

    fun startListening() {
        val currentModel = model
        if (currentModel == null) {
            error.value = "Modelo no cargado"
            return
        }

        try {
            val recognizer = Recognizer(currentModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let {
                        val json = JSONObject(it)
                        val partial = json.optString("partial", "")
                        viewModelScope.launch(Dispatchers.Main) {
                            partialText.value = partial
                        }
                    }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        val json = JSONObject(it)
                        val text = json.optString("text", "")
                        if (text.isNotEmpty()) {
                            viewModelScope.launch(Dispatchers.Main) {
                                insertText("$text ")
                                partialText.value = ""
                            }
                        }
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let {
                        val json = JSONObject(it)
                        val text = json.optString("text", "")
                        viewModelScope.launch(Dispatchers.Main) {
                            if (text.isNotEmpty()) {
                                insertText("$text ")
                            }
                            partialText.value = ""
                            stopListening()
                        }
                    }
                }

                override fun onError(exception: Exception?) {
                    viewModelScope.launch(Dispatchers.Main) {
                        error.value = "Error en reconocimiento: ${exception?.message}"
                        stopListening()
                    }
                }

                override fun onTimeout() {
                    viewModelScope.launch(Dispatchers.Main) {
                        stopListening()
                    }
                }
            })
            isListening.value = true
        } catch (e: Exception) {
            error.value = "Error al iniciar el dictado: ${e.message}"
        }
    }

    private fun insertText(textToInsert: String) {
        val current = textFieldValue.value
        val oldText = current.text

        // Evitar espacios dobles al inicio de la inserción
        val cleanedInsert = if (oldText.isNotEmpty() &&
            current.selection.start > 0 &&
            oldText[current.selection.start - 1] == ' ' &&
            textToInsert.startsWith(" ")) {
            textToInsert.substring(1)
        } else {
            textToInsert
        }

        val newText = oldText.substring(0, current.selection.start) +
                cleanedInsert +
                oldText.substring(current.selection.end)

        val newSelection = TextRange(current.selection.start + cleanedInsert.length)
        textFieldValue.value = TextFieldValue(newText, newSelection)
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
        isListening.value = false
        partialText.value = ""
    }

    fun updateTextFieldValue(newValue: TextFieldValue) {
        textFieldValue.value = newValue
    }

    fun deleteLastChar() {
        val current = textFieldValue.value
        val text = current.text
        if (text.isEmpty()) return

        if (!current.selection.collapsed) {
            val newText = text.removeRange(current.selection.start, current.selection.end)
            textFieldValue.value = TextFieldValue(newText, TextRange(current.selection.start))
        } else if (current.selection.start > 0) {
            val newText = text.removeRange(current.selection.start - 1, current.selection.start)
            textFieldValue.value = TextFieldValue(newText, TextRange(current.selection.start - 1))
        }
    }

    fun deleteLastWord() {
        val current = textFieldValue.value
        val text = current.text
        if (text.isEmpty()) return

        if (!current.selection.collapsed) {
            deleteLastChar()
            return
        }

        val textBeforeCursor = text.substring(0, current.selection.start)
        val trimmedBefore = textBeforeCursor.trimEnd()

        if (trimmedBefore.isEmpty()) {
            textFieldValue.value = TextFieldValue(text.substring(current.selection.start), TextRange(0))
            return
        }

        val lastSpace = trimmedBefore.lastIndexOf(' ')
        val newBefore = if (lastSpace == -1) "" else trimmedBefore.substring(0, lastSpace + 1)
        val newText = newBefore + text.substring(current.selection.start)

        textFieldValue.value = TextFieldValue(newText, TextRange(newBefore.length))
    }

    fun clearText() {
        textFieldValue.value = TextFieldValue("")
        partialText.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        speechService?.shutdown()
        model?.close()
    }
}