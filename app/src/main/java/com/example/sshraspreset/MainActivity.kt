package com.example.sshraspreset // Usando o seu package name original

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke // Import que você adicionou
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sshraspreset.ui.theme.SSHRASPRESETTheme // Seu tema
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SSHRASPRESETTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SshClientScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshClientScreen(modifier: Modifier = Modifier) {
    var commandInput by remember { mutableStateOf(TextFieldValue("")) }
    var outputText by remember { mutableStateOf("Aguardando comando...") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Estados para controle de conexão e IP
    var currentSshHost by remember { mutableStateOf("10.8.0.6") } // IP Inicial
    var ipInputError by remember { mutableStateOf<String?>(null) }
    var showIpInput by remember { mutableStateOf(false) } // Controla visibilidade do campo de IP
    var initialConnectionAttempted by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Tentando conexão inicial...") }


    // --- Configurações SSH (Usuário, Senha, Porta são fixas neste exemplo) ---
    val sshUser = "pi"            // Usuário do Pi
    val sshPassword = "nbr5410!" // Senha do Pi
    val sshPort = 22              // Porta padrão do SSH
    // --- Fim das Configurações SSH ---

    // Função para enviar comando SSH
    suspend fun sendSshCommandInternal(host: String, command: String): String {
        return withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelExec? = null
            try {
                val jsch = JSch()
                session = jsch.getSession(sshUser, host, sshPort)
                session.setPassword(sshPassword)
                val config = Properties()
                config["StrictHostKeyChecking"] = "no"
                session.setConfig(config)
                session.connect(7000) // Timeout de conexão reduzido para testes rápidos

                channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                val inputStream = channel.inputStream
                channel.connect(5000)

                val result = inputStream?.bufferedReader()?.readText() ?: "Nenhuma saída ou stream nulo."
                // Se a conexão for bem-sucedida com um comando simples (ex: "echo connected"), atualiza o status
                if (command == "echo connected_test_ok" && result.trim() == "connected_test_ok") {
                    connectionStatus = "Conectado a: $host"
                    showIpInput = false // Esconde o campo de IP se a conexão for bem-sucedida
                }
                result
            } catch (e: JSchException) {
                e.printStackTrace()
                if (!initialConnectionAttempted) { // Se for a primeira tentativa
                    connectionStatus = "Falha ao conectar a $host. Verifique o IP."
                    showIpInput = true // Mostra o campo para digitar novo IP
                }
                "Erro JSch: ${e.message}"
            } catch (e: Exception) {
                e.printStackTrace()
                "Erro Geral: ${e.message}"
            } finally {
                channel?.disconnect()
                session?.disconnect()
                if (!initialConnectionAttempted) {
                    initialConnectionAttempted = true
                }
            }
        }
    }

    // Tentativa de conexão inicial ao carregar a tela
    LaunchedEffect(Unit) { // Executa apenas uma vez quando o Composable entra na composição
        isLoading = true
        outputText = "Testando conexão com $currentSshHost..."
        // Usar um comando leve para testar a conexão
        val testResult = sendSshCommandInternal(currentSshHost, "echo connected_test_ok")
        if (testResult.trim() != "connected_test_ok") {
            // A mensagem de erro já será definida por sendSshCommandInternal
            showIpInput = true // Garante que o input de IP apareça se o teste falhar
        } else {
            outputText = "Conexão com $currentSshHost bem-sucedida."
            connectionStatus = "Conectado a: $currentSshHost"
        }
        isLoading = false
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(connectionStatus, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // Campo para entrada de IP, se necessário
        if (showIpInput) {
            OutlinedTextField(
                value = currentSshHost,
                onValueChange = { currentSshHost = it },
                label = { Text("IP do Raspberry Pi") },
                modifier = Modifier.fillMaxWidth(),
                isError = ipInputError != null,
                singleLine = true,
                enabled = !isLoading
            )
            if (ipInputError != null) {
                Text(text = ipInputError!!, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    keyboardController?.hide()
                    isLoading = true
                    outputText = "Testando conexão com $currentSshHost..."
                    coroutineScope.launch {
                        // Re-testa a conexão com o novo IP
                        val testResult = sendSshCommandInternal(currentSshHost, "echo connected_test_ok")
                        if (testResult.trim() != "connected_test_ok") {
                            // A mensagem de erro já será definida por sendSshCommandInternal
                        } else {
                            outputText = "Conexão com $currentSshHost bem-sucedida."
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !isLoading
            ) {
                Text("Tentar Conectar com este IP")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }


        Text("Comando SSH Personalizado", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = commandInput,
            onValueChange = { commandInput = it },
            label = { Text("Digite o comando (ex: ls -l)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading && !showIpInput // Desabilita se estiver configurando IP
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = {
                    val command = commandInput.text
                    if (command.isNotBlank() && !showIpInput) {
                        keyboardController?.hide()
                        isLoading = true
                        outputText = "Executando: $command em $currentSshHost"
                        coroutineScope.launch {
                            val result = sendSshCommandInternal(currentSshHost, command)
                            outputText = result
                            isLoading = false
                        }
                    } else if (showIpInput) {
                        outputText = "Primeiro conecte-se a um IP válido."
                    } else {
                        outputText = "Por favor, insira um comando."
                    }
                },
                enabled = !isLoading && !showIpInput, // Desabilita se estiver configurando IP
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Text(if (isLoading && commandInput.text.isNotBlank()) "Executando..." else "Enviar Comando")
            }

            // Botão para reiniciar
            Button(
                onClick = {
                    if (!showIpInput) {
                        keyboardController?.hide()
                        isLoading = true
                        val rebootCommand = "sudo reboot" // Comando de reinício
                        outputText = "Enviando comando de reinício para $currentSshHost..."
                        coroutineScope.launch {
                            val result = sendSshCommandInternal(currentSshHost, rebootCommand)
                            // Para o reboot, o resultado pode ser vazio ou uma mensagem de desconexão.
                            outputText = "Comando de reinício enviado. Resultado: $result"
                            // Não necessariamente indica sucesso, apenas que o comando foi enviado.
                            isLoading = false
                        }
                    } else {
                        outputText = "Primeiro conecte-se a um IP válido."
                    }
                },
                enabled = !isLoading && !showIpInput, // Desabilita se estiver configurando IP
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Text("Reiniciar Pi")
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        Text("Saída:", style = MaterialTheme.typography.titleSmall)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                text = outputText,
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SshClientScreenPreview() {
    SSHRASPRESETTheme {
        SshClientScreen()
    }
}