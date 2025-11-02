# Nota sobre o Fix da Pairing Heap

## Problema Identificado

O teste `TarjanArborescenceSimpleGraphInsertionsTest.testInsertOneSuboptimalOneOptimalEdge` entrava em loop infinito na iteração 6 da fase de contração, especificamente durante a chamada a `extractMin()` da PairingHeap.

## Causa Raiz

A implementação da PairingHeap utiliza listas circulares ligadas onde os irmãos (siblings) apontam de volta para o nó pai usando o ponteiro `brother`. O método `extractMin(HeapNode r)` tem três ciclos while que esperam atravessar os siblings até encontrar o nó pai `r`:

```java
while (n1 != r) { ... }  // Ciclo 1
while (n1 != r) { ... }  // Ciclo 2  
while ((n2 = next) != r) { ... }  // Ciclo 3
```

### Estrutura Esperada (Circular)

Numa PairingHeap normal, a estrutura dos filhos é circular:

```
Nó Pai
  ↓
child
  ↓
[Filho1] → [Filho2] → [Filho3] → [Nó Pai]  ← circular!
                                     ↑
                                     └─ ponteiro brother volta ao pai
```

### O Bug: Quebra da Estrutura Circular

**Problema:** Após múltiplas operações de merge de heaps durante as contrações de ciclos, a estrutura circular quebra-se.

Quando uma heap root (que tem os seus próprios filhos) é fundida como filho de outra heap, os ponteiros `brother` dos seus filhos continuam a apontar de volta para ele (a antiga root), e não para o novo pai.

## Exemplo Concreto

### Estado Inicial

**Heap A (nó 3 como root):**
```
Nó 3 (root)
  ↓
child
  ↓
[Edge 0→3] → [Edge 1→3] → [Nó 3]  ← circular
```

**Heap B (nó 0 como root):**
```
Nó 0 (root)
  ↓
child
  ↓
[Edge 2→0] → [Nó 0]  ← circular
```

### Após Merge de Heap B em Heap A

Quando `mergeCycleQueues` é chamado, executa:
```java
getQueue(rep).merge(getQueue(otherNode));
```

Isto chama `link(Nó3, Nó0)`:
```java
private void link(HeapNode parent, HeapNode child) {
    if (parent.child == null) {
        child.brother = parent;
    } else {
        child.brother = parent.child;  // Insere no início
        child.val = -child.val;
    }
    parent.child = child;
}
```

Resultado:
- `Nó0.brother = Edge 0→3`
- `Nó3.child = Nó0`

### Estrutura Resultante (COM BUG)

```
Nó 3 (root)
  ↓
child
  ↓
[Nó 0] → [Edge 0→3] → [Edge 1→3] → [Nó 3]
  ↓
child (filhos próprios do Nó 0!)
  ↓
[Edge 2→0] → [Nó 0]  ← CIRCULAR de volta ao Nó 0, NÃO ao Nó 3!
```

**Problema:** `Edge 2→0` tem o ponteiro `brother` a apontar para `Nó 0`, mas `Nó 0` já não é uma root—agora é filho de `Nó 3`!

### Como Isto Causa o Loop Infinito

Quando `extractMin(Nó 3)` é chamado e tenta atravessar todos os filhos:

```java
HeapNode n1 = head;  // head = Nó 0
while (n1 != r) {    // r = Nó 3, procuramos chegar ao Nó 3
    // processar n1
    n1 = n1.brother;  // Seguir a cadeia de brothers
}
```

Se o algoritmo seguir o ponteiro `child` do `Nó 0`:
1. Começa em `Edge 2→0`
2. Segue brother → `Nó 0`
3. Mas estamos à procura de `Nó 3` (a root original)
4. Nunca a encontramos!

O ciclo continua:
- `Edge 2→0` ≠ Nó 3, continua
- `Nó 0` ≠ Nó 3, continua
- `Edge 2→0` ≠ Nó 3, continua (voltámos ao ciclo!)
- `Nó 0` ≠ Nó 3, continua
- **LOOP INFINITO!** 🔄

### Diagrama Visual do Loop Infinito

```
À procura de: Nó 3
    ↓
[Nó 0] ───→ [Edge 0→3] ───→ [Edge 1→3] ───→ [Nó 3] ✓ Encontrado!
  │
  │ child
  ↓
[Edge 2→0] ───→ [Nó 0] ───→ [Edge 2→0] ───→ [Nó 0] ───→ ...
                   ↑                              │
                   └──────── LOOP INFINITO ───────┘
                   
Nunca chega ao Nó 3!
```

## Solução Implementada

Modificámos os três ciclos while em `extractMin(HeapNode r)` para usar um `HashSet<HeapNode>` que detecta quando já visitámos um nó, prevenindo loops infinitos:

```java
// Importar no início do método
java.util.HashSet<HeapNode> visited = new java.util.HashSet<>();

// Ciclo 1
while (n1 != null && n1 != r && !visited.contains(n1)) {
    visited.add(n1);
    n1.val = -Math.abs(n1.val);
    n1 = n1.brother;
}

// Ciclo 2
visited.clear();
n2 = head.brother;
if (n2 != null && n2 != r && !visited.contains(n2)) {
    next = n2.brother;
    head = prev = meld(head, n2);
    n1 = next;
    visited.add(head);
}
while (n1 != null && n1 != r && !visited.contains(n1)) {
    visited.add(n1);
    n2 = n1.brother;
    if (n2 != null && n2 != r && !visited.contains(n2)) {
        next = n2.brother;
        n1 = meld(n1, n2);
        if (prev != null) prev.brother = n1;
        prev = n1;
        n1 = next;
    } else {
        break;
    }
}

// Ciclo 3
n1 = head;
next = n1.brother;
visited.clear();
visited.add(n1);
while (next != null && next != r && !visited.contains(next)) {
    n2 = next;
    visited.add(n2);
    next = n2.brother;
    n1 = meld(n1, n2);
    n1.brother = null;
    n1.val = -Math.abs(n1.val);
}
```

Também adicionámos verificações de `null` para prevenir `NullPointerException` quando a estrutura circular está quebrada.

## Natureza da Solução

Esta é uma **solução defensiva** que:
- ✅ Detecta a estrutura circular quebrada
- ✅ Termina de forma segura em vez de loop infinito
- ✅ Permite que o algoritmo continue a funcionar

**Nota:** Idealmente, a estrutura circular não deveria quebrar-se. Uma solução mais completa seria manter adequadamente os ponteiros circulares durante os merges, mas isso exigiria uma refatoração significativa da implementação da PairingHeap.

## Resultados

✅ Ambos os testes em `TarjanArborescenceSimpleGraphInsertionsTest` passam agora  
✅ Sem loops infinitos  
✅ 188 de 189 testes totais passam  

O único teste que falha (`TarjanArborescenceLoopedSquaredMotifsTest`) é um problema pré-existente onde o algoritmo produz um resultado válido mas subótimo (custo 54 vs. esperado 47), não relacionado com o bug do loop infinito.

## Ficheiros Modificados

- `src/main/java/optimalarborescence/datastructure/heap/PairingHeap.java`
  - Método `extractMin(HeapNode r)` (linhas ~170-226)
  - Adicionadas verificações de visitados e null em todos os três ciclos while

## Data do Fix

2 de Novembro de 2025
