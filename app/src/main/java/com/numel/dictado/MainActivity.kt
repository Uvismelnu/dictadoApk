package com.numel.dictado

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.vosk.LibVosk
import org.vosk.LogLevel

class MainActivity : ComponentActivity() {
    private val viewModel: DictadoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LibVosk.setLogLevel(LogLevel.INFO)

        setContent {
            MaterialTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.initModel(this@MainActivity)
                    } else {
                        viewModel.error.value = "Permiso de audio denegado"
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DictadoScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun DictadoScreen(viewModel: DictadoViewModel) {
    val textFieldValue by viewModel.textFieldValue
    val partialText by viewModel.partialText
    val isListening by viewModel.isListening
    val error by viewModel.error
    val modelLoaded by viewModel.modelLoaded

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visualización inteligente: inserta el texto parcial en la posición del cursor
        val displayValue = if (isListening && partialText.isNotEmpty()) {
            val cursor = textFieldValue.selection.start
            val newText = textFieldValue.text.substring(0, cursor) +
                    partialText +
                    textFieldValue.text.substring(textFieldValue.selection.end)
            TextFieldValue(
                text = newText,
                selection = TextRange(cursor + partialText.length)
            )
        } else {
            textFieldValue
        }

        val scrollState = rememberScrollState()
        val interactionSource = remember { MutableInteractionSource() }

        BasicTextField(
            value = displayValue,
            onValueChange = { newValue ->
                if (!isListening) {
                    viewModel.updateTextFieldValue(newValue)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            readOnly = isListening,
            singleLine = false,
            maxLines = Int.MAX_VALUE,
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyLarge
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = displayValue.text,
                innerTextField = {
                    Box(modifier = Modifier.verticalScroll(scrollState)) {
                        innerTextField()
                    }
                },
                enabled = true,
                singleLine = false,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = { Text("Presiona 'Dictar' para comenzar...") },
                label = { Text("Texto dictado") },
                colors = OutlinedTextFieldDefaults.colors()
            )
        }

        // Fila de controles principales: Dictar y Copiar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isListening) viewModel.stopListening() else viewModel.startListening()
                },
                modifier = Modifier.weight(1.5f),
                enabled = modelLoaded
            ) {
                Text(if (isListening) "DETENER" else "DICTAR")
            }

            OutlinedButton(
                onClick = {
                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("text", textFieldValue.text)
                    clipboardManager.setPrimaryClip(clipData)
                    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                enabled = textFieldValue.text.isNotEmpty()
            ) {
                Text("COPIAR")
            }
            Button(
                onClick = { (context as Activity).finish() }
            ) {
                Text("SALIR")
            }

        }

        // Fila de edición y borrado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Borrar un carácter
            FilledTonalButton(
                onClick = { viewModel.deleteLastChar() },
                modifier = Modifier.weight(1f),
                enabled = !isListening && textFieldValue.text.isNotEmpty()
            ) {
                Text("Borrar")
            }

            // Borrar palabra completa
            FilledTonalButton(
                onClick = { viewModel.deleteLastWord() },
                modifier = Modifier.weight(1f),
                enabled = !isListening && textFieldValue.text.isNotEmpty()
            ) {
                Text("Palabra")
            }

            // Borrar todo
            Button(
                onClick = { viewModel.clearText() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                enabled = !isListening && textFieldValue.text.isNotEmpty()
            ) {
                Text("Todo")
            }
        }

        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (!modelLoaded && error.isEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Cargando modelo de voz...", style = MaterialTheme.typography.labelSmall)
        }
    }
}