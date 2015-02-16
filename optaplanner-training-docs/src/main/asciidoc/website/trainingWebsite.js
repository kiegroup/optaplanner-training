function toggleShowSolutionBlock(solutionBlock) {
    solutionBlock.prev().toggleClass('stacked');
    solutionBlock.toggle();
}

function insertShowSolutionButtons() {
    $('.solution').each(function(idx, node) {
        var solutionBlock = $(node);
        var showSolutionButton = $('<div><a class="show-solution" href="#">Show solution</a></div>');
        solutionBlock.prev().append(showSolutionButton);
        showSolutionButton.on('click', function(event) {
            event.preventDefault();
            toggleShowSolutionBlock(solutionBlock);
        });
        toggleShowSolutionBlock(solutionBlock);
    });
}

$(insertShowSolutionButtons);
